package com.migration.full.metadata;

import com.migration.db.DatabaseConnection;
import com.migration.model.ColumnInfo;
import com.migration.model.TableInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库元数据读取器
 */
public class MetadataReader {
    private static final Logger logger = LoggerFactory.getLogger(MetadataReader.class);
    
    private DatabaseConnection connection;

    public MetadataReader(DatabaseConnection connection) {
        this.connection = connection;
    }

    /**
     * 获取数据库中所有表的列表
     */
    public List<String> getAllTables() throws SQLException {
        List<String> tables = new ArrayList<>();
        
        String sql = "SHOW TABLES";
        try (Statement stmt = connection.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            
            logger.info("找到 {} 个表", tables.size());
        }
        
        return tables;
    }

    /**
     * 获取表的创建语句
     */
    public String getCreateTableSql(String tableName) throws SQLException {
        String sql = "SHOW CREATE TABLE " + tableName;
        
        try (Statement stmt = connection.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getString(2);
            }
        }
        
        return null;
    }

    /**
     * 获取表的详细信息
     */
    public TableInfo getTableInfo(String tableName) throws SQLException {
        TableInfo tableInfo = new TableInfo();
        tableInfo.setTableName(tableName);
        tableInfo.setCreateSql(getCreateTableSql(tableName));
        
        // 获取列信息
        String sql = "DESCRIBE " + tableName;
        try (Statement stmt = connection.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                ColumnInfo column = new ColumnInfo();
                column.setColumnName(rs.getString("Field"));
                column.setDataType(rs.getString("Type"));
                column.setNullable("YES".equals(rs.getString("Null")));
                column.setDefaultValue(rs.getString("Default"));
                column.setAutoIncrement("auto_increment".equalsIgnoreCase(rs.getString("Extra")));
                
                tableInfo.addColumn(column);
            }
        }
        
        // 获取主键信息
        getPrimaryKeyInfo(tableName, tableInfo);
        
        logger.debug("表 {} 的列信息: {}", tableName, tableInfo.getColumns().size());
        
        return tableInfo;
    }

    /**
     * 获取主键信息
     */
    private void getPrimaryKeyInfo(String tableName, TableInfo tableInfo) throws SQLException {
        String sql = "SHOW KEYS FROM " + tableName + " WHERE Key_name = 'PRIMARY'";
        
        try (Statement stmt = connection.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String columnName = rs.getString("Column_name");
                for (ColumnInfo column : tableInfo.getColumns()) {
                    if (column.getColumnName().equals(columnName)) {
                        column.setPrimaryKey(true);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 获取所有表的详细信息
     */
    public List<TableInfo> getAllTablesInfo() throws SQLException {
        List<TableInfo> tablesInfo = new ArrayList<>();
        List<String> tables = getAllTables();
        
        for (String tableName : tables) {
            try {
                TableInfo tableInfo = getTableInfo(tableName);
                tablesInfo.add(tableInfo);
            } catch (SQLException e) {
                logger.error("获取表 {} 的信息失败", tableName, e);
                throw e;
            }
        }
        
        return tablesInfo;
    }

    /**
     * 获取表的行数
     */
    public long getTableRowCount(String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        
        try (Statement stmt = connection.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        
        return 0;
    }
}
