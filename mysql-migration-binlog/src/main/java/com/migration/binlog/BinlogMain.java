package com.migration.binlog;

import com.migration.config.MigrationConfig;
import com.migration.db.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;

/**
 * MySQL 数据库 Binlog 监听工具主程序
 */
public class BinlogMain {
    private static final Logger logger = LoggerFactory.getLogger(BinlogMain.class);

    public static void main(String[] args) {
        String taskId = getTaskId(args);
        
        setupLogging(taskId);
        
        logger.info("========================================");
        logger.info("MySQL 数据库 Binlog 监听工具启动");
        logger.info("========================================");

        BinlogService binlogService = null;
        DatabaseConnection sourceConn = null;
        DatabaseConnection targetConn = null;

        try {
            String configFile = getConfigFile(args, taskId);
            logger.info("任务 ID: {}", taskId);
            logger.info("使用配置文件: {}", configFile);

            MigrationConfig config = taskId != null ? 
                new MigrationConfig(configFile, taskId) : new MigrationConfig(configFile);
            logger.info("源数据库: {}", config.getSourceConfig().getHost());
            logger.info("目标数据库: {}", config.getTargetConfig().getHost());

            sourceConn = new DatabaseConnection(config.getSourceConfig());
            targetConn = new DatabaseConnection(config.getTargetConfig());

            if (!sourceConn.testConnection()) {
                throw new SQLException("无法连接到源数据库");
            }
            if (!targetConn.testConnection()) {
                throw new SQLException("无法连接到目标数据库");
            }

            String sqlOutputDir;
            if (taskId != null) {
                sqlOutputDir = "files/" + taskId + "/sql_output";
            } else {
                sqlOutputDir = System.getProperty("sql.output.dir", System.getenv("SQL_OUTPUT_DIR"));
                if (sqlOutputDir == null) {
                    sqlOutputDir = "./sql_output";
                }
            }
            logger.info("SQL 输出目录: {}", sqlOutputDir);

            binlogService = new BinlogService(sourceConn, targetConn, sqlOutputDir);
            binlogService.setEventFilter(config.getIncludedDatabases(), config.getIncludedTables());

            logger.info("\n========================================");
            logger.info("启动 Binlog 监听");
            logger.info("========================================");

            binlogService.start();
            logger.info("Binlog 监听已启动，正在监听 binlog 变化...");
            logger.info("按 Ctrl+C 停止 Binlog 监听");

            while (binlogService.isRunning()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.info("接收到中断信号，停止 Binlog 监听");
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("Binlog 监听失败", e);
            System.exit(1);
        } finally {
            if (binlogService != null) {
                binlogService.stop();
                logger.info("Binlog 监听已停止");
            }
            
            if (sourceConn != null) {
                sourceConn.close();
            }
            if (targetConn != null) {
                targetConn.close();
            }
        }
    }
    
    private static void setupLogging(String taskId) {
        if (taskId != null) {
            String logPath = "files/" + taskId + "/logs";
            File logDir = new File(logPath);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            System.setProperty("LOG_PATH", logPath);
            System.setProperty("LOG_FILE", "binlog");
        }
    }

    /**
     * 获取任务 ID
     */
    private static String getTaskId(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--task-id".equals(args[i]) || "-t".equals(args[i])) {
                return args[i + 1];
            }
        }
        String taskId = System.getProperty("task.id");
        if (taskId != null && !taskId.isEmpty()) {
            return taskId;
        }
        taskId = System.getenv("TASK_ID");
        if (taskId != null && !taskId.isEmpty()) {
            return taskId;
        }
        return null;
    }

    /**
     * 获取配置文件路径
     */
    private static String getConfigFile(String[] args, String taskId) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i]) || "-c".equals(args[i])) {
                return args[i + 1];
            }
        }
        
        String defaultConfig;
        if (taskId != null) {
            defaultConfig = "files/" + taskId + "/config.properties";
        } else {
            defaultConfig = "config.properties";
        }
        
        File configFile = new File(defaultConfig);
        
        if (configFile.exists()) {
            return defaultConfig;
        }
        
        throw new RuntimeException("配置文件不存在: " + defaultConfig + 
                                 "\n请提供配置文件路径作为参数，或确保 config.properties 存在");
    }
}