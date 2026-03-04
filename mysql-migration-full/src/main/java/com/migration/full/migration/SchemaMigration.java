package com.migration.full.migration;

import com.migration.db.DatabaseConnection;
import com.migration.model.TableInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * 表结构迁移类
 */
public class SchemaMigration {
    private static final Logger logger = LoggerFactory.getLogger(SchemaMigration.class);
    
    private DatabaseConnection sourceConnection;
    private DatabaseConnection targetConnection;
    private boolean dropTables;

    public SchemaMigration(DatabaseConnection sourceConnection, DatabaseConnection targetConnection, boolean dropTables) {
        this.sourceConnection = sourceConnection;
        this.targetConnection = targetConnection;
        this.dropTables = dropTables;
    }

    /**
     * 迁移所有表结构
     */
    public void migrateAllTables(List<TableInfo> tables) throws SQLException {
        logger.info("开始迁移表结构，共 {} 个表", tables.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (TableInfo table : tables) {
            try {
                migrateTable(table);
                successCount++;
                logger.info("表 {} 结构迁移成功", table.getTableName());
            } catch (SQLException e) {
                failCount++;
                logger.error("表 {} 结构迁移失败，已忽略该错误继续执行", table.getTableName(), e);
                // 不抛出异常，继续迁移下一个表
            }
        }
        
        logger.info("表结构迁移完成，成功: {}, 失败: {}", successCount, failCount);
        
//        // 如果所有表都失败了，抛出异常
//        if (failCount == tables.size() && tables.size() > 0) {
//            throw new SQLException("所有表的结构迁移都失败了");
//        }
    }

    /**
     * 迁移单个表结构
     */
    public void migrateTable(TableInfo table) throws SQLException {
        String tableName = table.getTableName();
        
        // 如果需要，先删除目标表
        if (dropTables) {
            dropTableIfExists(tableName);
        }
        
        // 创建表
        createTable(table);
    }

    /**
     * 删除表（如果存在）
     */
    private void dropTableIfExists(String tableName) throws SQLException {
        String sql = "DROP TABLE IF EXISTS " + tableName;
        targetConnection.execute(sql);
        logger.debug("已删除表: {}", tableName);
    }

    /**
     * 创建表
     */
    private void createTable(TableInfo table) throws SQLException {
        String createSql = table.getCreateSql();
        
        // 移除表名中的数据库名前缀（如果有）
        createSql = cleanCreateSql(createSql);
        
        targetConnection.execute(createSql);
        logger.debug("已创建表: {}", table.getTableName())  ;
    }

    /**
     * 清理创建表的 SQL 语句
     */
    private String cleanCreateSql(String createSql) {
        // 移除数据库名前缀，例如：`database`.`table` -> `table`
        createSql = createSql.replaceAll("`[^`]+`\\.`", "`");
        
        // 移除 AUTO_INCREMENT 值（因为目标数据库可能已有数据）
        createSql = createSql.replaceAll("AUTO_INCREMENT=\\d+", "AUTO_INCREMENT=1");
        
        return createSql;
    }

    /**
     * 检查表是否已存在
     */
    public boolean tableExists(String tableName) throws SQLException {
        String sql = "SHOW TABLES LIKE '" + tableName + "'";
        
        try (var stmt = targetConnection.getConnection().createStatement();
             var rs = stmt.executeQuery(sql)) {
            
            return rs.next();
        }
    }
}
