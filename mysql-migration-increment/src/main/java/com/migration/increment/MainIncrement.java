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
        logger.info("========================================");
        logger.info("MySQL 增量同步工具启动");
        logger.info("========================================");

        // 加载配置
        Properties config = loadConfig();

        // 目标数据库配置
        String targetHost = config.getProperty("target.db.host", "localhost");
        int targetPort = Integer.parseInt(config.getProperty("target.db.port", "3306"));
        String targetDatabase = config.getProperty("target.db.database");
        String targetUsername = config.getProperty("target.db.username", "root");
        String targetPassword = config.getProperty("target.db.password", "");

        // SQL 文件目录
        String sqlDirectory = config.getProperty("sql.directory", "./sql_output");

        // Checkpoint 数据库路径
        String checkpointDbPath = config.getProperty("checkpoint.db.path", "./checkpoint/checkpoint");

        // 扫描间隔（毫秒）
        long scanIntervalMs = Long.parseLong(config.getProperty("sql.scan.interval.ms", "5000"));

        logger.info("目标数据库: {}:{}/{}", targetHost, targetPort, targetDatabase);
        logger.info("SQL 目录: {}", sqlDirectory);
        logger.info("Checkpoint 路径: {}", checkpointDbPath);
        logger.info("扫描间隔: {} ms", scanIntervalMs);

        // 创建目标数据库连接
        DatabaseConfig targetDbConfig = new DatabaseConfig(
                targetHost, targetPort, targetDatabase, targetUsername, targetPassword
        );
        DatabaseConnection targetConn = new DatabaseConnection(targetDbConfig);

        // 创建并启动增量同步（持续监听模式）
        IncrementExecutor executor = new IncrementExecutor(targetConn, checkpointDbPath, sqlDirectory, scanIntervalMs);

        // 添加关闭钩子，优雅退出
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("接收到关闭信号，正在停止增量同步...");
            executor.close();
            logger.info("增量同步已停止");
        }));

        try {
            // 启动持续监听
            executor.start();

            // 主线程保持运行
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
    private static Properties loadConfig() {
        Properties config = new Properties();
        File configFile = new File("config.properties");

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

        // 从环境变量覆盖
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
}
