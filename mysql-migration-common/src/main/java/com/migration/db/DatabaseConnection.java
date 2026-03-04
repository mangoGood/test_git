package com.migration.db;

import com.migration.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库连接管理类
 */
public class DatabaseConnection {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);
    
    private DatabaseConfig config;
    private Connection connection;

    public DatabaseConnection(DatabaseConfig config) {
        this.config = config;
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(
                    config.getJdbcUrl(),
                    config.getUsername(),
                    config.getPassword()
                );
                logger.info("成功连接到数据库: {}", config.getDatabase());
            } catch (ClassNotFoundException e) {
                logger.error("MySQL JDBC 驱动未找到", e);
                throw new SQLException("MySQL JDBC 驱动未找到", e);
            } catch (SQLException e) {
                logger.error("连接数据库失败: {}", config.getDatabase(), e);
                throw e;
            }
        }
        return connection;
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.info("数据库连接已关闭: {}", config.getDatabase());
                }
            } catch (SQLException e) {
                logger.error("关闭数据库连接失败", e);
            }
        }
    }

    /**
     * 执行 SQL 语句
     */
    public void execute(String sql) throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * 测试连接
     */
    public boolean testConnection() {
        try {
            return getConnection() != null && !getConnection().isClosed();
        } catch (SQLException e) {
            logger.error("测试连接失败", e);
            return false;
        }
    }
    
    /**
     * 获取数据库配置
     */
    public DatabaseConfig getConfig() {
        return config;
    }
}
