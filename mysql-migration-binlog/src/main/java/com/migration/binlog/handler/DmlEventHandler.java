package com.migration.binlog.handler;

import com.migration.binlog.core.BinlogEvent;
import com.migration.binlog.core.BinlogPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DML 事件处理器
 * 处理 INSERT、UPDATE、DELETE 语句，将 SQL 写入文件
 */
public class DmlEventHandler implements BinlogEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(DmlEventHandler.class);

    private SqlFileManager sqlFileManager;

    public DmlEventHandler(String outputDirectory) {
        this.sqlFileManager = new SqlFileManager(outputDirectory);
    }

    @Override
    public boolean handle(BinlogEvent event) {
        if (!supports(event)) {
            return false;
        }

        BinlogEvent.DmlEventData dmlData = (BinlogEvent.DmlEventData) event.getData();
        String database = event.getDatabase();
        String table = event.getTable();
        BinlogPosition position = event.getPosition();

        logger.info("处理 DML 事件: {}.{}, type={}, position={}", database, table, dmlData.getDmlType(), position);

        try {
            switch (dmlData.getDmlType()) {
                case INSERT:
                    return handleInsert(database, table, dmlData.getAfterRows(), position);
                case UPDATE:
                    return handleUpdate(database, table, dmlData.getBeforeRows(), dmlData.getAfterRows(), position);
                case DELETE:
                    return handleDelete(database, table, dmlData.getBeforeRows(), position);
                default:
                    logger.warn("未知的 DML 类型: {}", dmlData.getDmlType());
                    return false;
            }
        } catch (Exception e) {
            logger.error("处理 DML 事件失败: {}.{}, type={}", database, table, dmlData.getDmlType(), e);
            return false;
        }
    }

    @Override
    public boolean supports(BinlogEvent event) {
        return event != null && event.isDmlEvent();
    }

    /**
     * 处理 INSERT 操作
     */
    private boolean handleInsert(String database, String table, List<Map<String, Serializable>> rows, BinlogPosition position) {
        if (rows == null || rows.isEmpty()) {
            logger.warn("rows 为空或 null");
            return true;
        }

        String fullTableName = database + "." + table;

        for (Map<String, Serializable> row : rows) {
            logger.debug("处理行: {}", row);
            
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(fullTableName).append(" (");

            // 构建列名
            String[] columns = row.keySet().toArray(new String[0]);
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(columns[i]);
            }

            sql.append(") VALUES (");

            // 构建值
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(formatValue(row.get(columns[i])));
            }
            sql.append(")");

            String sqlStr = sql.toString();
            logger.debug("生成的 SQL: {}", sqlStr);
            
            // 写入 SQL 文件（带位置信息）
            sqlFileManager.writeSql(sqlStr, position);
        }

        logger.debug("已处理 INSERT: {}.{}, 行数: {}", database, table, rows.size());
        return true;
    }

    /**
     * 处理 UPDATE 操作
     */
    private boolean handleUpdate(String database, String table,
                                 List<Map<String, Serializable>> beforeRows,
                                 List<Map<String, Serializable>> afterRows,
                                 BinlogPosition position) {
        if (beforeRows == null || afterRows == null || beforeRows.isEmpty()) {
            return true;
        }

        String fullTableName = database + "." + table;

        for (int i = 0; i < afterRows.size(); i++) {
            Map<String, Serializable> beforeRow = beforeRows.get(i);
            Map<String, Serializable> afterRow = afterRows.get(i);

            StringBuilder sql = new StringBuilder("UPDATE ");
            sql.append(fullTableName).append(" SET ");

            // 构建 SET 子句
            String[] columns = afterRow.keySet().toArray(new String[0]);
            for (int j = 0; j < columns.length; j++) {
                if (j > 0) sql.append(", ");
                sql.append(columns[j]).append(" = ").append(formatValue(afterRow.get(columns[j])));
            }

            // 构建 WHERE 子句
            sql.append(" WHERE ");
            String[] beforeColumns = beforeRow.keySet().toArray(new String[0]);
            for (int j = 0; j < beforeColumns.length; j++) {
                if (j > 0) sql.append(" AND ");
                sql.append(beforeColumns[j]).append(" = ").append(formatValue(beforeRow.get(beforeColumns[j])));
            }

            // 写入 SQL 文件（带位置信息）
            sqlFileManager.writeSql(sql.toString(), position);
        }

        logger.debug("已处理 UPDATE: {}.{}, 行数: {}", database, table, afterRows.size());
        return true;
    }

    /**
     * 处理 DELETE 操作
     */
    private boolean handleDelete(String database, String table, List<Map<String, Serializable>> rows, BinlogPosition position) {
        if (rows == null || rows.isEmpty()) {
            return true;
        }

        String fullTableName = database + "." + table;

        for (Map<String, Serializable> row : rows) {
            StringBuilder sql = new StringBuilder("DELETE FROM ");
            sql.append(fullTableName).append(" WHERE ");

            // 构建 WHERE 子句
            String[] columns = row.keySet().toArray(new String[0]);
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(" AND ");
                sql.append(columns[i]).append(" = ").append(formatValue(row.get(columns[i])));
            }

            // 写入 SQL 文件（带位置信息）
            sqlFileManager.writeSql(sql.toString(), position);
        }

        logger.debug("已处理 DELETE: {}.{}, 行数: {}", database, table, rows.size());
        return true;
    }

    /**
     * 格式化值
     */
    private String formatValue(Serializable value) {
        if (value == null) {
            return "NULL";
        }
        
        if (value instanceof Number) {
            return value.toString();
        }
        
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "1" : "0";
        }
        
        // 字符串类型，需要转义
        String strValue = value.toString();
        strValue = strValue.replace("\\", "\\\\")
                           .replace("'", "\\'")
                           .replace("\n", "\\n")
                           .replace("\r", "\\r")
                           .replace("\t", "\\t");
        return "'" + strValue + "'";
    }

    /**
     * 获取 SQL 文件管理器
     */
    public SqlFileManager getSqlFileManager() {
        return sqlFileManager;
    }

    /**
     * 关闭处理器
     */
    public void close() {
        if (sqlFileManager != null) {
            sqlFileManager.close();
        }
    }
}
