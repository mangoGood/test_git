package com.migration.agent.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class RecoveryTask implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String taskId;
    private String taskName;
    private Long userId;
    private String sourceConnection;
    private String targetConnection;
    private String migrationMode;
    private String status;
    private int progress;
    private LocalDateTime createdAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSourceConnection() {
        return sourceConnection;
    }

    public void setSourceConnection(String sourceConnection) {
        this.sourceConnection = sourceConnection;
    }

    public String getTargetConnection() {
        return targetConnection;
    }

    public void setTargetConnection(String targetConnection) {
        this.targetConnection = targetConnection;
    }

    public String getMigrationMode() {
        return migrationMode;
    }

    public void setMigrationMode(String migrationMode) {
        this.migrationMode = migrationMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public TaskMessage toTaskMessage() {
        TaskMessage message = new TaskMessage();
        message.setTaskId(this.taskId);
        message.setTaskName(this.taskName);
        message.setUserId(this.userId);
        message.setSourceConnection(this.sourceConnection);
        message.setTargetConnection(this.targetConnection);
        message.setMigrationMode(this.migrationMode);
        message.setCreatedAt(this.createdAt);
        return message;
    }
}
