package com.migration.increment.checkpoint;

import com.migration.binlog.core.BinlogPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Checkpoint 管理器
 * 使用 H2 数据库存储和读取同步位点信息
 */
public class CheckpointManager {
    private static final Logger logger = LoggerFactory.getLogger(CheckpointManager.class);
    
    private static final String DB_URL_PREFIX = "jdbc:h2:file:";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    
    private String dbPath;
    private Connection connection;
    
    public CheckpointManager(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }
    
    /**
     * 初始化数据库
     */
    private void initDatabase() {
        try {
            String url = DB_URL_PREFIX + dbPath;
            connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD);
            
            // 创建 checkpoint 表
            String createTableSql = "CREATE TABLE IF NOT EXISTS checkpoint (" +
                    "id INT PRIMARY KEY," +
                    "filename VARCHAR(255)," +
                    "position BIGINT," +
                    "gtid VARCHAR(255)," +
                    "timestamp BIGINT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSql);
            }
            
            logger.info("Checkpoint 数据库初始化成功: {}", dbPath);
        } catch (SQLException e) {
            logger.error("初始化 Checkpoint 数据库失败", e);
            throw new RuntimeException("无法初始化 Checkpoint 数据库", e);
        }
    }
    
    /**
     * 保存位点信息
     */
    public void saveCheckpoint(BinlogPosition position) {
        if (position == null) {
            logger.warn("尝试保存空的位点信息");
            return;
        }
        
        String sql = "MERGE INTO checkpoint (id, filename, position, gtid, timestamp) " +
                "VALUES (1, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, position.getFilename());
            stmt.setLong(2, position.getPosition());
            stmt.setString(3, position.getGtid());
            stmt.setLong(4, position.getTimestamp());
            
            stmt.executeUpdate();
            logger.info("保存 Checkpoint 成功: {}", position);
        } catch (SQLException e) {
            logger.error("保存 Checkpoint 失败", e);
            throw new RuntimeException("无法保存 Checkpoint", e);
        }
    }
    
    /**
     * 读取位点信息
     */
    public BinlogPosition loadCheckpoint() {
        String sql = "SELECT filename, position, gtid, timestamp FROM checkpoint WHERE id = 1";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                String filename = rs.getString("filename");
                long position = rs.getLong("position");
                String gtid = rs.getString("gtid");
                long timestamp = rs.getLong("timestamp");
                
                BinlogPosition binlogPosition = new BinlogPosition(filename, position, gtid);
                binlogPosition.setTimestamp(timestamp);
                
                logger.info("加载 Checkpoint 成功: {}", binlogPosition);
                return binlogPosition;
            }
        } catch (SQLException e) {
            logger.error("加载 Checkpoint 失败", e);
        }
        
        logger.info("未找到 Checkpoint 记录");
        return null;
    }
    
    /**
     * 关闭数据库连接
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Checkpoint 数据库连接已关闭");
            } catch (SQLException e) {
                logger.error("关闭 Checkpoint 数据库连接失败", e);
            }
        }
    }
}
