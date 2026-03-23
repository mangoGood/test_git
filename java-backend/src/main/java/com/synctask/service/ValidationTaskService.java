package com.synctask.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.synctask.entity.ValidationTask;
import com.synctask.entity.ValidationTaskLog;
import com.synctask.entity.Workflow;
import com.synctask.entity.WorkflowStatus;
import com.synctask.repository.ValidationTaskLogRepository;
import com.synctask.repository.ValidationTaskRepository;
import com.synctask.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ValidationTaskService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationTaskService.class);

    @Autowired
    private ValidationTaskRepository validationTaskRepository;

    @Autowired
    private ValidationTaskLogRepository validationTaskLogRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    private final Gson gson = new Gson();

    private static final Pattern CONNECTION_PATTERN = Pattern.compile(
        "mysql://([^:]+):([^@]+)@([^:]+):(\\d+)(?:/(.*))?"
    );

    public static class ParsedConnection {
        public String username;
        public String password;
        public String host;
        public int port;
        public String database;

        public ParsedConnection(String username, String password, String host, int port, String database) {
            this.username = username;
            this.password = password;
            this.host = host;
            this.port = port;
            this.database = database;
        }
    }

    public ParsedConnection parseConnection(String connectionStr) {
        if (connectionStr == null || connectionStr.isEmpty()) {
            throw new IllegalArgumentException("连接串不能为空");
        }

        Matcher matcher = CONNECTION_PATTERN.matcher(connectionStr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("连接串格式不正确");
        }

        return new ParsedConnection(
            matcher.group(1),
            matcher.group(2),
            matcher.group(3),
            Integer.parseInt(matcher.group(4)),
            matcher.group(5)
        );
    }

    private String buildJdbcUrl(String host, int port, String database) {
        if (database != null && !database.isEmpty()) {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true", host, port, database);
        }
        return String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true", host, port);
    }

    public List<Workflow> getIncrementalWorkflows(Long userId) {
        List<Workflow> workflows = workflowRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
        return workflows.stream()
            .filter(w -> w.getStatus() == WorkflowStatus.INCREMENT_RUNNING)
            .toList();
    }

    public Page<ValidationTask> getValidationTasks(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return validationTaskRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable);
    }

    public ValidationTask getValidationTask(String id, Long userId) {
        return validationTaskRepository.findByIdAndUserIdAndIsDeletedFalse(id, userId)
            .orElseThrow(() -> new RuntimeException("校验任务不存在"));
    }

    @Transactional
    public ValidationTask createValidationTask(String workflowId, Long userId) {
        Workflow workflow = workflowRepository.findByIdAndUserIdAndIsDeletedFalse(workflowId, userId)
            .orElseThrow(() -> new RuntimeException("同步任务不存在"));

        if (workflow.getStatus() != WorkflowStatus.INCREMENT_RUNNING) {
            throw new RuntimeException("只能为增量同步中的任务创建校验任务");
        }

        ValidationTask task = new ValidationTask();
        task.setName(workflow.getName() + "-校验-" + System.currentTimeMillis());
        task.setWorkflowId(workflowId);
        task.setWorkflowName(workflow.getName());
        task.setUserId(userId);
        task.setSourceConnection(workflow.getSourceConnection());
        task.setTargetConnection(workflow.getTargetConnection());
        task.setSyncObjects(workflow.getSyncObjects());
        task.setStatus(ValidationTask.ValidationStatus.PENDING);

        validationTaskRepository.save(task);
        addLog(task.getId(), ValidationTaskLog.LogLevel.INFO, "校验任务已创建，等待执行");

        executeValidationAsync(task.getId());

        return task;
    }

    @Async
    @Transactional
    public void executeValidationAsync(String taskId) {
        ValidationTask task = validationTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            logger.error("校验任务不存在: {}", taskId);
            return;
        }

        try {
            task.setStatus(ValidationTask.ValidationStatus.RUNNING);
            task.setStartedAt(java.time.LocalDateTime.now());
            validationTaskRepository.save(task);
            addLog(taskId, ValidationTaskLog.LogLevel.INFO, "开始执行数据校验");

            ParsedConnection sourceConn = parseConnection(task.getSourceConnection());
            ParsedConnection targetConn = parseConnection(task.getTargetConnection());

            Map<String, Map<String, List<String>>> syncObjects = parseSyncObjects(task.getSyncObjects());

            int totalTables = 0;
            int passedTables = 0;
            int failedTables = 0;
            long totalRows = 0;
            long mismatchedRows = 0;

            try (Connection sourceDb = DriverManager.getConnection(
                    buildJdbcUrl(sourceConn.host, sourceConn.port, sourceConn.database),
                    sourceConn.username, sourceConn.password);
                 Connection targetDb = DriverManager.getConnection(
                    buildJdbcUrl(targetConn.host, targetConn.port, targetConn.database),
                    targetConn.username, targetConn.password)) {

                for (Map.Entry<String, Map<String, List<String>>> dbEntry : syncObjects.entrySet()) {
                    String dbName = dbEntry.getKey();
                    List<String> tables = dbEntry.getValue().get("tables");

                    if (tables == null || tables.isEmpty()) continue;

                    for (String tableName : tables) {
                        totalTables++;
                        addLog(taskId, ValidationTaskLog.LogLevel.INFO, 
                            "校验表: " + dbName + "." + tableName);

                        try {
                            TableDiffResult diff = compareTableData(
                                sourceDb, targetDb, dbName, tableName);

                            totalRows += diff.totalRows;
                            mismatchedRows += diff.mismatchedRows;

                            if (diff.mismatchedRows == 0 && diff.error == null) {
                                passedTables++;
                                addLog(taskId, ValidationTaskLog.LogLevel.INFO, 
                                    "表 " + dbName + "." + tableName + " 校验通过，共 " + diff.totalRows + " 行");
                            } else {
                                failedTables++;
                                if (diff.error != null) {
                                    addLog(taskId, ValidationTaskLog.LogLevel.ERROR, 
                                        "表 " + dbName + "." + tableName + " 校验失败: " + diff.error);
                                } else {
                                    addLog(taskId, ValidationTaskLog.LogLevel.WARNING, 
                                        "表 " + dbName + "." + tableName + " 数据不一致，差异行数: " + diff.mismatchedRows);
                                }
                            }
                        } catch (Exception e) {
                            failedTables++;
                            addLog(taskId, ValidationTaskLog.LogLevel.ERROR, 
                                "表 " + dbName + "." + tableName + " 校验异常: " + e.getMessage());
                        }
                    }
                }
            }

            task.setTotalTables(totalTables);
            task.setPassedTables(passedTables);
            task.setFailedTables(failedTables);
            task.setTotalRows(totalRows);
            task.setMismatchedRows(mismatchedRows);
            task.setStatus(ValidationTask.ValidationStatus.COMPLETED);
            task.setCompletedAt(java.time.LocalDateTime.now());
            validationTaskRepository.save(task);

            addLog(taskId, ValidationTaskLog.LogLevel.INFO, 
                String.format("校验完成: 共 %d 个表，通过 %d 个，失败 %d 个，总行数 %d，差异行数 %d",
                    totalTables, passedTables, failedTables, totalRows, mismatchedRows));

        } catch (Exception e) {
            logger.error("校验任务执行失败: {}", e.getMessage(), e);
            task.setStatus(ValidationTask.ValidationStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(java.time.LocalDateTime.now());
            validationTaskRepository.save(task);
            addLog(taskId, ValidationTaskLog.LogLevel.ERROR, "校验任务执行失败: " + e.getMessage());
        }
    }

    private Map<String, Map<String, List<String>>> parseSyncObjects(String syncObjectsJson) {
        if (syncObjectsJson == null || syncObjectsJson.isEmpty()) {
            return new HashMap<>();
        }
        Type type = new TypeToken<Map<String, Map<String, List<String>>>>(){}.getType();
        return gson.fromJson(syncObjectsJson, type);
    }

    private static class TableDiffResult {
        long totalRows;
        long mismatchedRows;
        String error;
    }

    private TableDiffResult compareTableData(Connection sourceDb, Connection targetDb, 
            String dbName, String tableName) {
        TableDiffResult result = new TableDiffResult();

        try {
            String sourceHash = getTableChecksum(sourceDb, dbName, tableName);
            String targetHash = getTableChecksum(targetDb, dbName, tableName);

            result.totalRows = getRowCount(sourceDb, dbName, tableName);

            if (sourceHash != null && sourceHash.equals(targetHash)) {
                result.mismatchedRows = 0;
            } else {
                result.mismatchedRows = countMismatchedRows(sourceDb, targetDb, dbName, tableName);
            }

        } catch (SQLException e) {
            result.error = e.getMessage();
        }

        return result;
    }

    private String getTableChecksum(Connection conn, String dbName, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("CHECKSUM TABLE `" + dbName + "`.`" + tableName + "`")) {
            if (rs.next()) {
                return rs.getString("Checksum");
            }
        } catch (SQLException e) {
            logger.warn("获取表校验和失败: {}.{} - {}", dbName, tableName, e.getMessage());
        }
        return null;
    }

    private long getRowCount(Connection conn, String dbName, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM `" + dbName + "`.`" + tableName + "`")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    private long countMismatchedRows(Connection sourceDb, Connection targetDb, 
            String dbName, String tableName) throws SQLException {
        List<String> primaryKeys = getPrimaryKeys(sourceDb, dbName, tableName);
        
        if (primaryKeys.isEmpty()) {
            return -1;
        }

        String pkColumns = String.join("`, `", primaryKeys);
        String orderClause = String.join(", ", primaryKeys);

        String sourceQuery = String.format(
            "SELECT `%s`, MD5(CONCAT_WS('|', *)) as row_hash FROM `%s`.`%s` ORDER BY %s",
            pkColumns, dbName, tableName, orderClause);
        String targetQuery = String.format(
            "SELECT `%s`, MD5(CONCAT_WS('|', *)) as row_hash FROM `%s`.`%s` ORDER BY %s",
            pkColumns, dbName, tableName, orderClause);

        long mismatched = 0;

        try (Statement sourceStmt = sourceDb.createStatement();
             ResultSet sourceRs = sourceStmt.executeQuery(sourceQuery);
             Statement targetStmt = targetDb.createStatement();
             ResultSet targetRs = targetStmt.executeQuery(targetQuery)) {

            while (sourceRs.next() && targetRs.next()) {
                String sourceHash = sourceRs.getString("row_hash");
                String targetHash = targetRs.getString("row_hash");

                if (!Objects.equals(sourceHash, targetHash)) {
                    mismatched++;
                }
            }

            while (sourceRs.next()) mismatched++;
            while (targetRs.next()) mismatched++;
        }

        return mismatched;
    }

    private List<String> getPrimaryKeys(Connection conn, String dbName, String tableName) throws SQLException {
        List<String> pks = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getPrimaryKeys(dbName, null, tableName)) {
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pks;
    }

    @Transactional
    public void deleteValidationTask(String id, Long userId) {
        ValidationTask task = getValidationTask(id, userId);
        task.setIsDeleted(true);
        validationTaskRepository.save(task);
        addLog(id, ValidationTaskLog.LogLevel.INFO, "校验任务已删除");
    }

    private void addLog(String taskId, ValidationTaskLog.LogLevel level, String message) {
        ValidationTaskLog log = new ValidationTaskLog();
        log.setValidationTaskId(taskId);
        log.setLevel(level);
        log.setMessage(message);
        validationTaskLogRepository.save(log);
    }
}
