package com.migration.increment;

import com.migration.config.DatabaseConfig;
import com.migration.db.DatabaseConnection;
import com.migration.increment.executor.IncrementExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * 增量同步主类
 * 从 SQL 文件中读取并执行大于 checkpoint 位点的 SQL
 */
public class MainIncrement {
    private static final Logger logger = LoggerFactory.getLogger(MainIncrement.class);

    public static void main(String[] args) {
        String taskId = args.length > 0 ? args[0] : null;
        
        setupLogging(taskId);
        
        logger.info("========================================");
        logger.info("MySQL 增量同步工具启动");
        logger.info("========================================");

        if (taskId != null) {
            logger.info("任务ID: {}", taskId);
        }

        Properties config = loadConfig(taskId);

        String targetHost = config.getProperty("target.db.host", "localhost");
        int targetPort = Integer.parseInt(config.getProperty("target.db.port", "3306"));
        String targetDatabase = config.getProperty("target.db.database");
        String targetUsername = config.getProperty("target.db.username", "root");
        String targetPassword = config.getProperty("target.db.password", "");

        String sqlDirectory = config.getProperty("sql.directory", "./sql_output");
        String checkpointDbPath = config.getProperty("checkpoint.db.path", "./checkpoint/checkpoint");
        long scanIntervalMs = Long.parseLong(config.getProperty("sql.scan.interval.ms", "5000"));

        if (taskId != null) {
            sqlDirectory = "./files/" + taskId + "/sql_output";
            checkpointDbPath = "./files/" + taskId + "/checkpoint/checkpoint";
        }

        logger.info("目标数据库: {}:{}/{}", targetHost, targetPort, targetDatabase);
        logger.info("SQL 目录: {}", sqlDirectory);
        logger.info("Checkpoint 路径: {}", checkpointDbPath);
        logger.info("扫描间隔: {} ms", scanIntervalMs);

        DatabaseConfig targetDbConfig = new DatabaseConfig(
                targetHost, targetPort, targetDatabase, targetUsername, targetPassword
        );
        DatabaseConnection targetConn = new DatabaseConnection(targetDbConfig);

        IncrementExecutor executor = new IncrementExecutor(targetConn, checkpointDbPath, sqlDirectory, scanIntervalMs);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("接收到关闭信号，正在停止增量同步...");
            executor.close();
            logger.info("增量同步已停止");
        }));

        try {
            executor.start();

            logger.info("增量同步正在运行，按 Ctrl+C 停止...");
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("主线程被中断");
            Thread.currentThread().interrupt();
        } finally {
            executor.close();
        }

        logger.info("========================================");
        logger.info("增量同步完成");
        logger.info("========================================");
    }

    /**
     * 加载配置文件
     */
    private static Properties loadConfig(String taskId) {
        Properties config = new Properties();
        
        File configFile;
        if (taskId != null) {
            configFile = new File("files/" + taskId + "/config.properties");
        } else {
            configFile = new File("config.properties");
        }

        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
                logger.info("加载配置文件: {}", configFile.getAbsolutePath());
            } catch (IOException e) {
                logger.warn("加载配置文件失败，使用默认配置", e);
            }
        } else {
            logger.warn("配置文件不存在，使用默认配置: {}", configFile.getAbsolutePath());
        }

        overrideFromEnv(config, "target.db.host", "TARGET_HOST");
        overrideFromEnv(config, "target.db.port", "TARGET_PORT");
        overrideFromEnv(config, "target.db.database", "TARGET_DATABASE");
        overrideFromEnv(config, "target.db.username", "TARGET_USERNAME");
        overrideFromEnv(config, "target.db.password", "TARGET_PASSWORD");
        overrideFromEnv(config, "sql.directory", "SQL_DIRECTORY");
        overrideFromEnv(config, "checkpoint.db.path", "CHECKPOINT_DB_PATH");
        overrideFromEnv(config, "sql.scan.interval.ms", "SQL_SCAN_INTERVAL_MS");

        return config;
    }

    /**
     * 从环境变量覆盖配置
     */
    private static void overrideFromEnv(Properties config, String key, String envKey) {
        String value = System.getenv(envKey);
        if (value != null && !value.isEmpty()) {
            config.setProperty(key, value);
        }
    }
    
    /**
     * 设置日志路径
     */
    private static void setupLogging(String taskId) {
        if (taskId != null) {
            String logPath = "files/" + taskId + "/logs";
            File logDir = new File(logPath);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            System.setProperty("LOG_PATH", logPath);
            System.setProperty("LOG_FILE", "increment");
        }
    }
}
