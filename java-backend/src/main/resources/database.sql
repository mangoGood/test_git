-- 创建数据库
CREATE DATABASE IF NOT EXISTS sync_task_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE sync_task_db;

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    email VARCHAR(100) UNIQUE COMMENT '邮箱',
    role VARCHAR(20) DEFAULT 'USER' COMMENT '角色',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
ƒ
-- 创建工作流任务表
CREATE TABLE IF NOT EXISTS workflows (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '任务名称',
    source_connection VARCHAR(255) COMMENT '源连接信息',
    target_connection VARCHAR(255) COMMENT '目标连接信息',
    status ENUM('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'PAUSED') DEFAULT 'PENDING' COMMENT '任务状态',
    progress INT DEFAULT 0 COMMENT '进度百分比',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    migration_mode ENUM('full', 'fullAndIncre') DEFAULT 'full' COMMENT '迁移模式：full-全量同步，fullAndIncre-全量+增量同步',
    is_deleted TINYINT(1) DEFAULT 0 COMMENT '是否软删除',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at TIMESTAMP NULL COMMENT '完成时间',
    error_message TEXT COMMENT '错误信息',
    is_billing TINYINT(1) DEFAULT 0 COMMENT '是否计费中',
    INDEX idx_status (status),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_is_deleted (is_deleted),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步任务工作流表';

-- 创建工作流日志表
CREATE TABLE IF NOT EXISTS workflow_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    workflow_id VARCHAR(36) NOT NULL,
    level ENUM('INFO', 'WARNING', 'ERROR') DEFAULT 'INFO' COMMENT '日志级别',
    message TEXT NOT NULL COMMENT '日志内容',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE CASCADE,
    INDEX idx_workflow_id (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流日志表';

-- 插入默认管理员账号 (密码: admin123)
INSERT INTO users (username, password, email, role, enabled) 
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin@example.com', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE username=username;

-- 插入测试用户账号 (密码: user123)
INSERT INTO users (username, password, email, role, enabled) 
VALUES ('user1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'user1@example.com', 'USER', 1)
ON DUPLICATE KEY UPDATE username=username;

INSERT INTO users (username, password, email, role, enabled) 
VALUES ('user2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'user2@example.com', 'USER', 1)
ON DUPLICATE KEY UPDATE username=username;
