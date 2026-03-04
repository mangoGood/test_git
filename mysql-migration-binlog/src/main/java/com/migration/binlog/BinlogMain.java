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
        logger.info("========================================");
        logger.info("MySQL 数据库 Binlog 监听工具启动");
        logger.info("========================================");

        BinlogService binlogService = null;
        DatabaseConnection sourceConn = null;
        DatabaseConnection targetConn = null;

        try {
            // 检查配置文件
            String configFile = getConfigFile(args);
            logger.info("使用配置文件: {}", configFile);

            // 加载配置
            MigrationConfig config = new MigrationConfig(configFile);
            logger.info("源数据库: {}", config.getSourceConfig().getHost());
            logger.info("目标数据库: {}", config.getTargetConfig().getHost());

            // 创建数据库连接
            sourceConn = new DatabaseConnection(config.getSourceConfig());
            targetConn = new DatabaseConnection(config.getTargetConfig());

            // 测试连接
            if (!sourceConn.testConnection()) {
                throw new SQLException("无法连接到源数据库");
            }
            if (!targetConn.testConnection()) {
                throw new SQLException("无法连接到目标数据库");
            }

            // 获取 SQL 输出目录（从系统属性或环境变量）
            String sqlOutputDir = System.getProperty("sql.output.dir", System.getenv("SQL_OUTPUT_DIR"));
            if (sqlOutputDir == null) {
                sqlOutputDir = "./sql_output";
            }
            logger.info("SQL 输出目录: {}", sqlOutputDir);

            // 创建并配置 binlog 服务
            binlogService = new BinlogService(sourceConn, targetConn, sqlOutputDir);
            binlogService.setEventFilter(config.getIncludedDatabases(), config.getIncludedTables());

            // 启动 binlog 监听
            logger.info("\n========================================");
            logger.info("启动 Binlog 监听");
            logger.info("========================================");

            binlogService.start();
            logger.info("Binlog 监听已启动，正在监听 binlog 变化...");
            logger.info("按 Ctrl+C 停止 Binlog 监听");

            // 保持运行，监听 binlog
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
            // 关闭 binlog 服务
            if (binlogService != null) {
                binlogService.stop();
                logger.info("Binlog 监听已停止");
            }
            
            // 关闭数据库连接
            if (sourceConn != null) {
                sourceConn.close();
            }
            if (targetConn != null) {
                targetConn.close();
            }
        }
    }

    /**
     * 获取配置文件路径
     */
    private static String getConfigFile(String[] args) {
        if (args.length > 0) {
            return args[0];
        }
        
        // 默认配置文件
        String defaultConfig = "config.properties";
        File configFile = new File(defaultConfig);
        
        if (configFile.exists()) {
            return defaultConfig;
        }
        
        throw new RuntimeException("配置文件不存在: " + defaultConfig + 
                                 "\n请提供配置文件路径作为参数，或确保 config.properties 存在");
    }
}