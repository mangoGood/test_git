package com.migration.agent.service;

import com.migration.agent.model.TaskStateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;

public class TaskStateService {
    private static final Logger logger = LoggerFactory.getLogger(TaskStateService.class);
    
    private static final String STATE_DIR = "files";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    
    public TaskStateService() {
        initializeDatabase();
    }
    
    private void initializeDatabase() {
        File stateDir = new File(STATE_DIR);
        if (!stateDir.exists()) {
            stateDir.mkdirs();
        }
    }
    
    private Connection getConnection(String taskId) throws SQLException {
        String dbUrl = "jdbc:h2:./files/" + taskId + "/metadata;MODE=MySQL;AUTO_SERVER=TRUE";
        Connection conn = DriverManager.getConnection(dbUrl, DB_USER, DB_PASSWORD);
        
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS task_state (" +
                "task_id VARCHAR(36) PRIMARY KEY, " +
                "task_name VARCHAR(255), " +
                "user_id BIGINT, " +
                "migration_mode VARCHAR(50), " +
                "source_connection VARCHAR(500), " +
                "target_connection VARCHAR(500), " +
                "status VARCHAR(50), " +
                "progress INT DEFAULT 0, " +
                "last_processed_table VARCHAR(255), " +
                "last_updated BIGINT, " +
                "created_at VARCHAR(100)" +
                ")"
            );
        }
        
        return conn;
    }
    
    public void saveTaskState(TaskStateInfo stateInfo) {
        String taskId = stateInfo.getTaskId();
        
        try (Connection conn = getConnection(taskId)) {
            String sql = "MERGE INTO task_state (task_id, task_name, user_id, migration_mode, " +
                        "source_connection, target_connection, status, progress, " +
                        "last_processed_table, last_updated, created_at) " +
                        "KEY (task_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, stateInfo.getTaskId());
                stmt.setString(2, stateInfo.getTaskName());
                stmt.setLong(3, stateInfo.getUserId() != null ? stateInfo.getUserId() : 0);
                stmt.setString(4, stateInfo.getMigrationMode());
                stmt.setString(5, stateInfo.getSourceConnection());
                stmt.setString(6, stateInfo.getTargetConnection());
                stmt.setString(7, stateInfo.getStatus());
                stmt.setInt(8, stateInfo.getProgress());
                stmt.setString(9, stateInfo.getLastProcessedTable());
                stmt.setLong(10, System.currentTimeMillis());
                stmt.setString(11, stateInfo.getCreatedAt() != null ? stateInfo.getCreatedAt().toString() : null);
                
                stmt.executeUpdate();
            }
            
            logger.info("Task state saved to H2 database for task: {}", taskId);
            
        } catch (SQLException e) {
            logger.error("Error saving task state to H2 for task: {}", taskId, e);
            throw new RuntimeException("Failed to save task state to H2", e);
        }
    }
    
    public TaskStateInfo getTaskState(String taskId) {
        try (Connection conn = getConnection(taskId)) {
            String sql = "SELECT * FROM task_state WHERE task_id = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, taskId);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    TaskStateInfo stateInfo = new TaskStateInfo();
                    stateInfo.setTaskId(rs.getString("task_id"));
                    stateInfo.setTaskName(rs.getString("task_name"));
                    stateInfo.setUserId(rs.getLong("user_id"));
                    stateInfo.setMigrationMode(rs.getString("migration_mode"));
                    stateInfo.setSourceConnection(rs.getString("source_connection"));
                    stateInfo.setTargetConnection(rs.getString("target_connection"));
                    stateInfo.setStatus(rs.getString("status"));
                    stateInfo.setProgress(rs.getInt("progress"));
                    stateInfo.setLastProcessedTable(rs.getString("last_processed_table"));
                    stateInfo.setLastUpdated(rs.getLong("last_updated"));
                    
                    String createdAtStr = rs.getString("created_at");
                    if (createdAtStr != null && !createdAtStr.isEmpty()) {
                        try {
                            stateInfo.setCreatedAt(java.time.LocalDateTime.parse(createdAtStr));
                        } catch (Exception e) {
                            logger.warn("Failed to parse created_at: {}", createdAtStr);
                        }
                    }
                    
                    logger.info("Task state loaded from H2 database for task: {}, status: {}, migrationMode: {}", 
                        taskId, stateInfo.getStatus(), stateInfo.getMigrationMode());
                    return stateInfo;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error loading task state from H2 for task: {}", taskId, e);
        }
        
        logger.debug("No saved state found in H2 for task: {}", taskId);
        return null;
    }
    
    public void deleteTaskState(String taskId) {
        try (Connection conn = getConnection(taskId)) {
            String sql = "DELETE FROM task_state WHERE task_id = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, taskId);
                int rows = stmt.executeUpdate();
                
                if (rows > 0) {
                    logger.info("Task state deleted from H2 for task: {}", taskId);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error deleting task state from H2 for task: {}", taskId, e);
        }
    }
}
