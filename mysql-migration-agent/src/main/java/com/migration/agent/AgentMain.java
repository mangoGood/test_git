package com.migration.agent;

import com.migration.agent.manager.MigrationTaskManager;
import com.migration.agent.manager.ProcessManager;
import com.migration.agent.model.TaskMessage;
import com.migration.agent.model.TaskStatusMessage;
import com.migration.agent.service.ConfigService;
import com.migration.agent.service.KafkaConsumerService;
import com.migration.agent.service.KafkaProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.*;

public class AgentMain {
    private static final Logger logger = LoggerFactory.getLogger(AgentMain.class);
    
    private static final String KAFKA_BOOTSTRAP_SERVERS = "192.168.117.2:19092";
    private static final String CONSUMER_GROUP_ID = "migration-agent-group";
    private static final String METADATA_DB_URL = "jdbc:h2:./metadata";
    private static final String METADATA_DB_USER = "sa";
    private static final String METADATA_DB_PASSWORD = "";
    
    private static final String BINLOG_JAR_PATH = "mysql-migration-binlog/target/mysql-migration-binlog-1.0.0.jar";
    private static final String MIGRATION_FULL_JAR_PATH = "mysql-migration-full/target/mysql-migration-full-1.0.0.jar";
    
    private static final long BINLOG_MONITOR_INTERVAL = 30000;
    
    private KafkaConsumerService kafkaConsumer;
    private KafkaProducerService kafkaProducer;
    private ConfigService configService;
    private ProcessManager binlogManager;
    private MigrationTaskManager migrationTaskManager;
    private ScheduledExecutorService binlogMonitorExecutor;
    
    private volatile String currentTaskId;
    private volatile boolean binlogStarted = false;
    
    public static void main(String[] args) {
        AgentMain agent = new AgentMain();
        agent.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down agent...");
            agent.stop();
        }));
    }
    
    public void start() {
        logger.info("Starting Migration Agent...");
        
        initializeMetadataDatabase();
        
        kafkaProducer = new KafkaProducerService(KAFKA_BOOTSTRAP_SERVERS);
        configService = new ConfigService();
        
        binlogManager = new ProcessManager(BINLOG_JAR_PATH, "BinlogMain");
        
        kafkaConsumer = new KafkaConsumerService(KAFKA_BOOTSTRAP_SERVERS, CONSUMER_GROUP_ID, 
            this::handleTaskMessage);
        
        kafkaConsumer.start();
        
        binlogMonitorExecutor = Executors.newSingleThreadScheduledExecutor();
        binlogMonitorExecutor.scheduleAtFixedRate(this::monitorBinlogProcess, 
            BINLOG_MONITOR_INTERVAL, BINLOG_MONITOR_INTERVAL, TimeUnit.MILLISECONDS);
        
        logger.info("Migration Agent started successfully");
    }
    
    private void initializeMetadataDatabase() {
        try (Connection conn = DriverManager.getConnection(METADATA_DB_URL, METADATA_DB_USER, METADATA_DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            
            String createTableSql = "CREATE TABLE IF NOT EXISTS migration_progress (" +
                                    "task_id VARCHAR(255) PRIMARY KEY, " +
                                    "progress INT NOT NULL, " +
                                    "updated_at TIMESTAMP NOT NULL)";
            stmt.execute(createTableSql);
            
            logger.info("Metadata database initialized");
        } catch (Exception e) {
            logger.error("Error initializing metadata database", e);
            throw new RuntimeException("Failed to initialize metadata database", e);
        }
    }
    
    private void handleTaskMessage(TaskMessage taskMessage) {
        String taskId = taskMessage.getTaskId();
        String migrationMode = taskMessage.getMigrationMode();
        logger.info("Received task message: {} with migration mode: {}", taskId, migrationMode);
        
        try {
            if (currentTaskId != null && !currentTaskId.equals(taskId)) {
                logger.warn("New task received while previous task {} is still running", currentTaskId);
                stopCurrentTask();
            }
            
            currentTaskId = taskId;
            
            sendStatus(taskId, "RECEIVED", "Task received, preparing migration", 0);
            
            configService.updateConfig(taskMessage);
            logger.info("Config updated for task: {}", taskId);
            
            if ("fullAndIncre".equals(migrationMode)) {
                if (!binlogStarted) {
                    binlogManager.start();
                    binlogStarted = true;
                    sendStatus(taskId, "BINLOG_STARTED", "Binlog monitoring started", 0);
                }
            } else {
                logger.info("Full migration mode, skipping binlog process");
            }
            
            if (migrationTaskManager != null && migrationTaskManager.isRunning()) {
                logger.warn("Migration task is already running for task: {}", taskId);
                return;
            }
            
            migrationTaskManager = new MigrationTaskManager(
                MIGRATION_FULL_JAR_PATH, taskId, kafkaProducer,
                METADATA_DB_URL, METADATA_DB_USER, METADATA_DB_PASSWORD
            );
            
            migrationTaskManager.start();
            sendStatus(taskId, "MIGRATION_STARTED", "Full migration started", 0);
            
            logger.info("Migration task started for: {}", taskId);
            
        } catch (Exception e) {
            logger.error("Error handling task message: {}", taskId, e);
            sendStatus(taskId, "FAILED", "Error: " + e.getMessage(), 0);
        }
    }
    
    private void monitorBinlogProcess() {
        if (binlogStarted && currentTaskId != null) {
            try {
                binlogManager.ensureRunning();
            } catch (Exception e) {
                logger.error("Error monitoring binlog process", e);
                sendStatus(currentTaskId, "WARNING", "Binlog process monitoring error", 
                    getProgressFromDatabase(currentTaskId));
            }
        }
    }
    
    private void stopCurrentTask() {
        if (migrationTaskManager != null) {
            migrationTaskManager.stop();
            migrationTaskManager = null;
        }
        
        if (binlogStarted) {
            binlogManager.stop();
            binlogStarted = false;
        }
        
        currentTaskId = null;
    }
    
    private void sendStatus(String taskId, String status, String message, int progress) {
        TaskStatusMessage statusMessage = new TaskStatusMessage();
        statusMessage.setTaskId(taskId);
        statusMessage.setStatus(status);
        statusMessage.setMessage(message);
        statusMessage.setProgress(progress);
        
        kafkaProducer.sendStatus(statusMessage);
    }
    
    private int getProgressFromDatabase(String taskId) {
        try (Connection conn = DriverManager.getConnection(METADATA_DB_URL, METADATA_DB_USER, METADATA_DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement("SELECT progress FROM migration_progress WHERE task_id = ?")) {
            
            stmt.setString(1, taskId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("progress");
            }
        } catch (Exception e) {
            logger.error("Error getting progress from database for task: {}", taskId, e);
        }
        
        return 0;
    }
    
    public void stop() {
        logger.info("Stopping Migration Agent...");
        
        if (binlogMonitorExecutor != null) {
            binlogMonitorExecutor.shutdown();
            try {
                if (!binlogMonitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    binlogMonitorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                binlogMonitorExecutor.shutdownNow();
            }
        }
        
        if (kafkaConsumer != null) {
            kafkaConsumer.stop();
        }
        
        stopCurrentTask();
        
        if (kafkaProducer != null) {
            kafkaProducer.close();
        }
        
        logger.info("Migration Agent stopped");
    }
}