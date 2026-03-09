package com.migration.agent.service;

import com.migration.agent.model.RecoveryTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class RecoveryService {
    private static final Logger logger = LoggerFactory.getLogger(RecoveryService.class);
    
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    
    public RecoveryService(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }
    
    public List<RecoveryTask> getUnfinishedTasks() {
        List<RecoveryTask> tasks = new ArrayList<>();
        
        String sql = "SELECT id, name, user_id, source_connection, target_connection, " +
                     "migration_mode, status, progress, created_at " +
                     "FROM workflows " +
                     "WHERE status IN ('STARTING', 'FULL_MIGRATING', 'FULL_COMPLETED', 'INCREMENT_RUNNING') " +
                     "AND is_deleted = 0 " +
                     "ORDER BY created_at ASC";
        
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                RecoveryTask task = new RecoveryTask();
                task.setTaskId(rs.getString("id"));
                task.setTaskName(rs.getString("name"));
                task.setUserId(rs.getLong("user_id"));
                task.setSourceConnection(rs.getString("source_connection"));
                task.setTargetConnection(rs.getString("target_connection"));
                task.setMigrationMode(rs.getString("migration_mode"));
                task.setStatus(rs.getString("status"));
                task.setProgress(rs.getInt("progress"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                if (createdAt != null) {
                    task.setCreatedAt(createdAt.toLocalDateTime());
                }
                
                tasks.add(task);
                logger.info("Found unfinished task: id={}, name={}, status={}, mode={}", 
                    task.getTaskId(), task.getTaskName(), task.getStatus(), task.getMigrationMode());
            }
            
            logger.info("Total unfinished tasks found: {}", tasks.size());
            
        } catch (SQLException e) {
            logger.error("Error querying unfinished tasks from database", e);
        }
        
        return tasks;
    }
    
    public RecoveryTask getTaskById(String taskId) {
        String sql = "SELECT id, name, user_id, source_connection, target_connection, " +
                     "migration_mode, status, progress, created_at " +
                     "FROM workflows WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, taskId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                RecoveryTask task = new RecoveryTask();
                task.setTaskId(rs.getString("id"));
                task.setTaskName(rs.getString("name"));
                task.setUserId(rs.getLong("user_id"));
                task.setSourceConnection(rs.getString("source_connection"));
                task.setTargetConnection(rs.getString("target_connection"));
                task.setMigrationMode(rs.getString("migration_mode"));
                task.setStatus(rs.getString("status"));
                task.setProgress(rs.getInt("progress"));
                
                Timestamp createdAt = rs.getTimestamp("created_at");
                if (createdAt != null) {
                    task.setCreatedAt(createdAt.toLocalDateTime());
                }
                return task;
            }
            
        } catch (SQLException e) {
            logger.error("Error querying task by id: {}", taskId, e);
        }
        
        return null;
    }
}
