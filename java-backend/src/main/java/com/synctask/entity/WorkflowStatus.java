package com.synctask.entity;

public enum WorkflowStatus {
    PENDING,
    STARTING,           // 启动中
    FULL_MIGRATING,     // 全量同步中
    FULL_COMPLETED,     // 全量同步完成
    INCREMENT_RUNNING,  // 增量同步中
    COMPLETED,
    FAILED,
    PAUSED
}
