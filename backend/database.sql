-- 创建数据库
CREATE DATABASE IF NOT EXISTS sync_task_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE sync_task_db;

-- 创建工作流任务表
CREATE TABLE IF NOT EXISTS workflows (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '任务名称',
    source_connection VARCHAR(255) COMMENT '源连接信息',
    target_connection VARCHAR(255) COMMENT '目标连接信息',
    status ENUM('pending', 'running', 'completed', 'failed', 'paused') DEFAULT 'pending' COMMENT '任务状态',
    progress INT DEFAULT 0 COMMENT '进度百分比',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at TIMESTAMP NULL COMMENT '完成时间',
    error_message TEXT COMMENT '错误信息',
    is_billing TINYINT(1) DEFAULT 0 COMMENT '是否计费中',
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步任务工作流表';

-- 创建工作流日志表
CREATE TABLE IF NOT EXISTS workflow_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    workflow_id VARCHAR(36) NOT NULL,
    level ENUM('info', 'warning', 'error') DEFAULT 'info' COMMENT '日志级别',
    message TEXT NOT NULL COMMENT '日志内容',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE CASCADE,
    INDEX idx_workflow_id (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流日志表';
