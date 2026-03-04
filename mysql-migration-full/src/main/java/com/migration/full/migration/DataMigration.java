package com.migration.full.migration;

import com.migration.db.DatabaseConnection;
import com.migration.model.TableInfo;
import com.migration.full.progress.MigrationProgress;
import com.migration.full.progress.ProgressManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据迁移类
 */
public class DataMigration {
    private static final Logger logger = LoggerFactory.getLogger(DataMigration.class);
    
    private DatabaseConnection sourceConnection;
    private DatabaseConnection targetConnection;
    private int batchSize;
    private boolean continueOnError;
    private ProgressManager progressManager;

    public DataMigration(DatabaseConnection sourceConnection, DatabaseConnection targetConnection, 
                        int batchSize, boolean continueOnError, ProgressManager progressManager) {
        this.sourceConnection = sourceConnection;
        this.targetConnection = targetConnection;
        this.batchSize = batchSize;
        this.continueOnError = continueOnError;
        this.progressManager = progressManager;
    }

    /**
     * 迁移所有表的数据
     */
    public void migrateAllData(List<TableInfo> tables) throws SQLException {
        logger.info("开始迁移数据，共 {} 个表", tables.size());
        
        int totalSuccessCount = 0;
        int totalFailCount = 0;
        
        for (TableInfo table : tables) {
            try {
                int[] result = migrateTableData(table);
                totalSuccessCount += result[0];
                totalFailCount += result[1];
                logger.info("表 {} 数据迁移完成，成功: {}, 失败: {}", 
                           table.getTableName(), result[0], result[1]);
            } catch (SQLException e) {
                logger.error("表 {} 数据迁移失败", table.getTableName(), e);
                if (progressManager != null && progressManager.isEnabled()) {
                    progressManager.failMigration(table.getTableName(), e.getMessage());
                }
                if (!continueOnError) {
                    throw e;
                }
            }
        }
        
        logger.info("数据迁移完成，总成功: {}, 总失败: {}", totalSuccessCount, totalFailCount);
    }

    /**
     * 迁移单个表的数据
     */
    public int[] migrateTableData(TableInfo table) throws SQLException {
        String tableName = table.getTableName();
        
        // 获取总行数
        long totalRows = getTableRowCount(tableName);
        logger.info("开始迁移表 {} 的数据，总行数: {}", tableName, totalRows);
        
        if (totalRows == 0) {
            logger.info("表 {} 没有数据，跳过", tableName);
            return new int[]{0, 0};
        }
        
        // 获取列名
        List<String> columns = getColumnNames(table);
        String columnList = String.join(", ", columns);
        
        // 获取主键列名（用于断点续传）
        String primaryKeyColumn = getPrimaryKeyColumn(table);
        
        // 批量插入数据
        return migrateDataBatch(tableName, columnList, totalRows, primaryKeyColumn);
    }

    /**
     * 批量迁移数据
     */
    private int[] migrateDataBatch(String tableName, String columnList, long totalRows, String primaryKeyColumn) throws SQLException {
        int successCount = 0;
        int failCount = 0;
        
        // 检查是否有之前的迁移进度
        MigrationProgress progress = null;
        Long lastMigratedId = null;
        long startOffset = 0;
        
        if (progressManager != null && progressManager.isEnabled()) {
            try {
                progress = progressManager.startMigration(tableName, totalRows);
                if (progress != null && progress.getLastMigratedId() != 0) {
                    lastMigratedId = progress.getLastMigratedId();
                    startOffset = progress.getMigratedRows();
                    logger.info("从上次中断位置继续迁移，已迁移: {}, 最后ID: {}", startOffset, lastMigratedId);
                }
            } catch (SQLException e) {
                logger.error("获取迁移进度失败", e);
            }
        }
        
        // 构建查询SQL
        String selectSql;
        if (lastMigratedId != null && primaryKeyColumn != null) {
            selectSql = "SELECT " + columnList + " FROM " + tableName + 
                       " WHERE `" + primaryKeyColumn + "` > ? ORDER BY `" + primaryKeyColumn + "`";
        } else {
            selectSql = "SELECT " + columnList + " FROM " + tableName;
        }
        
        // 插入数据
        String insertSql = "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + 
                          String.join(", ", createPlaceholders(columnList.split(", ").length)) + ")";
        
        try (Connection sourceConn = sourceConnection.getConnection();
             PreparedStatement selectStmt = sourceConn.prepareStatement(selectSql);
             Connection targetConn = targetConnection.getConnection();
             PreparedStatement insertStmt = targetConn.prepareStatement(insertSql)) {
            
            // 设置断点续传参数
            if (lastMigratedId != null && primaryKeyColumn != null) {
                selectStmt.setLong(1, lastMigratedId);
            }
            
            try (ResultSet rs = selectStmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                int batchCount = 0;
                long processedRows = startOffset;
                Long currentLastId = lastMigratedId;
                
                while (rs.next()) {
                    try {
                        // 设置参数
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            insertStmt.setObject(i, value);
                        }
                        
                        // 获取当前行的主键值
                        if (primaryKeyColumn != null) {
                            for (int i = 1; i <= columnCount; i++) {
                                String columnName = metaData.getColumnName(i);
                                if (columnName.equals(primaryKeyColumn)) {
                                    Object idValue = rs.getObject(i);
                                    if (idValue instanceof Number) {
                                        currentLastId = ((Number) idValue).longValue();
                                    }
                                    break;
                                }
                            }
                        }
                        
                        // 添加到批处理
                        insertStmt.addBatch();
                        batchCount++;
                        
                        // 执行批处理
                        if (batchCount >= batchSize) {
                            int[] results = insertStmt.executeBatch();
                            successCount += countSuccess(results);
                            failCount += countFailures(results);
                            batchCount = 0;
                            
                            // 更新进度
                            if (progressManager != null && progressManager.isEnabled()) {
                                progressManager.updateProgress(tableName, processedRows, currentLastId);
                            }
                        }
                        
                        processedRows++;
                        
                        // 显示进度
                        if (processedRows % 10000 == 0) {
                            logger.info("表 {} 已处理 {}/{} 行", tableName, processedRows, totalRows);
                        }
                        
                    } catch (SQLException e) {
                        failCount++;
                        logger.error("插入数据失败，表: {}, 行: {}", tableName, processedRows, e);
                        
                        // 更新进度（即使失败也记录）
                        if (progressManager != null && progressManager.isEnabled()) {
                            try {
                                progressManager.updateProgress(tableName, processedRows, currentLastId);
                            } catch (SQLException ex) {
                                logger.error("更新进度失败", ex);
                            }
                        }
                        
                        if (!continueOnError) {
                            throw e;
                        }
                    }
                }
                
                // 执行剩余的批处理
                if (batchCount > 0) {
                    try {
                        int[] results = insertStmt.executeBatch();
                        successCount += countSuccess(results);
                        failCount += countFailures(results);
                        
                        // 更新最终进度
                        if (progressManager != null && progressManager.isEnabled()) {
                            progressManager.updateProgress(tableName, processedRows, currentLastId);
                        }
                    } catch (SQLException e) {
                        logger.error("执行最后一批数据失败，表: {}", tableName, e);
                        if (!continueOnError) {
                            throw e;
                        }
                    }
                }
                
                logger.info("表 {} 数据迁移完成，成功: {}, 失败: {}", tableName, successCount, failCount);
                
                // 标记迁移完成
                if (progressManager != null && progressManager.isEnabled()) {
                    try {
                        progressManager.completeMigration(tableName);
                    } catch (SQLException e) {
                        logger.error("标记迁移完成失败", e);
                    }
                }
            }
        }
        
        return new int[]{successCount, failCount};
    }

    /**
     * 获取表的行数
     */
    private long getTableRowCount(String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        
        try (Statement stmt = sourceConnection.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        
        return 0;
    }

    /**
     * 获取列名列表
     */
    private List<String> getColumnNames(TableInfo table) {
        List<String> columns = new ArrayList<>();
        for (var column : table.getColumns()) {
            columns.add("`" + column.getColumnName() + "`");
        }
        return columns;
    }

    /**
     * 获取主键列名
     */
    private String getPrimaryKeyColumn(TableInfo table) {
        for (var column : table.getColumns()) {
            if (column.isPrimaryKey()) {
                return column.getColumnName();
            }
        }
        return null;
    }

    /**
     * 创建占位符
     */
    private String[] createPlaceholders(int count) {
        String[] placeholders = new String[count];
        for (int i = 0; i < count; i++) {
            placeholders[i] = "?";
        }
        return placeholders;
    }

    /**
     * 统计成功数量
     */
    private int countSuccess(int[] results) {
        int count = 0;
        for (int result : results) {
            if (result >= 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计失败数量
     */
    private int countFailures(int[] results) {
        int count = 0;
        for (int result : results) {
            if (result < 0) {
                count++;
            }
        }
        return count;
    }
}
