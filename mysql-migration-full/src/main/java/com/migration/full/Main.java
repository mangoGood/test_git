package com.migration.full;

import com.migration.config.MigrationConfig;
import com.migration.db.DatabaseConnection;
import com.migration.full.checkpoint.CheckpointRecorder;
import com.migration.full.metadata.MetadataReader;
import com.migration.full.migration.DataMigration;
import com.migration.full.migration.SchemaMigration;
import com.migration.model.TableInfo;
import com.migration.full.progress.ProgressManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * MySQL 数据库全量迁移工具主程序
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("MySQL 数据库全量迁移工具启动");
        logger.info("========================================");

        ProgressManager progressManager = null;

        try {
            // 检查配置文件
            String configFile = getConfigFile(args);
            logger.info("使用配置文件: {}", configFile);

            // 加载配置
            MigrationConfig config = new MigrationConfig(configFile);
            logger.info("源数据库: {}", config.getSourceConfig().getDatabase());
            logger.info("目标数据库: {}", config.getTargetConfig().getDatabase());

            // 创建进度管理器
            progressManager = new ProgressManager(config.isEnableResume());
            
            // 检查是否有未完成的迁移
            if (progressManager.isEnabled() && progressManager.hasIncompleteProgress()) {
                logger.info("\n========================================");
                logger.info("检测到未完成的迁移");
                logger.info("========================================");
                progressManager.printProgressSummary();
                logger.info("将从上次中断的位置继续迁移\n");
            }

            // 创建数据库连接
            DatabaseConnection sourceConn = new DatabaseConnection(config.getSourceConfig());
            DatabaseConnection targetConn = new DatabaseConnection(config.getTargetConfig());

            // 测试连接
            if (!sourceConn.testConnection()) {
                throw new SQLException("无法连接到源数据库");
            }
            if (!targetConn.testConnection()) {
                throw new SQLException("无法连接到目标数据库");
            }

            // 记录源数据库快照位点（用于增量同步）
            String checkpointDbPath = config.getCheckpointDbPath();
            if (checkpointDbPath != null && !checkpointDbPath.isEmpty()) {
                CheckpointRecorder checkpointRecorder = new CheckpointRecorder(checkpointDbPath);
                try {
                    checkpointRecorder.recordSnapshot(sourceConn);
                } finally {
                    checkpointRecorder.close();
                }
            }

            // 读取源数据库元数据
            MetadataReader metadataReader = new MetadataReader(sourceConn);
            List<TableInfo> tables = metadataReader.getAllTablesInfo();
            
            if (tables.isEmpty()) {
                logger.warn("源数据库中没有找到任何表");
                return;
            }

            logger.info("找到 {} 个表需要迁移", tables.size());
            for (TableInfo table : tables) {
                long rowCount = metadataReader.getTableRowCount(table.getTableName());
                logger.info("  - {}: {} 行", table.getTableName(), rowCount);
            }

            // 迁移表结构
            if (config.isCreateTables()) {
                logger.info("\n========================================");
                logger.info("开始迁移表结构");
                logger.info("========================================");
                
                SchemaMigration schemaMigration = new SchemaMigration(
                    sourceConn, targetConn, config.isDropTables()
                );
                schemaMigration.migrateAllTables(tables);
                logger.info("表结构迁移完成");
            }

            // 迁移数据
            if (config.isMigrateData()) {
                logger.info("\n========================================");
                logger.info("开始迁移数据");
                logger.info("========================================");
                
                DataMigration dataMigration = new DataMigration(
                    sourceConn, targetConn, 
                    config.getBatchSize(), 
                    config.isContinueOnError(),
                    progressManager
                );
                dataMigration.migrateAllData(tables);
                logger.info("数据迁移完成");
            }

            // 打印迁移进度摘要
            if (progressManager.isEnabled()) {
                logger.info("\n========================================");
                logger.info("迁移进度摘要");
                logger.info("========================================");
                progressManager.printProgressSummary();
            }

            // 关闭连接
            sourceConn.close();
            targetConn.close();

            logger.info("\n========================================");
            logger.info("数据库全量迁移成功完成！");
            logger.info("========================================");

        } catch (Exception e) {
            logger.error("数据库全量迁移失败", e);
            System.exit(1);
        } finally {
            // 关闭进度管理器
            if (progressManager != null) {
                progressManager.close();
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