package com.migration.agent.thread;

import com.migration.agent.manager.ProcessManager;
import com.migration.agent.model.TaskMessage;
import com.migration.agent.model.TaskStatusMessage;
import com.migration.agent.service.KafkaProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class MigrationAgentThread implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MigrationAgentThread.class);
    
    private static final String BINLOG_JAR_PATH = "mysql-migration-binlog/target/mysql-migration-binlog-1.0.0.jar";
    private static final String MIGRATION_FULL_JAR_PATH = "mysql-migration-full/target/mysql-migration-full-1.0.0.jar";
    private static final String MIGRATION_INCREMENT_JAR_PATH = "mysql-migration-increment/target/mysql-migration-increment-1.0.0.jar";
    
    private static final long BINLOG_MONITOR_INTERVAL = 30000;
    private static final long INCREMENT_MONITOR_INTERVAL = 30000;
    private static final long STATUS_REPORT_INTERVAL = 60000;
    
    private final TaskMessage taskMessage;
    private final KafkaProducerService kafkaProducer;
    private final String taskId;
    private final AtomicBoolean running;
    private final AtomicBoolean stopped;
    
    private ProcessManager binlogProcess;
    private ProcessManager fullProcess;
    private ProcessManager incrementProcess;
    
    private Thread binlogMonitorThread;
    private Thread incrementMonitorThread;
    
    public MigrationAgentThread(TaskMessage taskMessage, KafkaProducerService kafkaProducer) {
        this.taskMessage = taskMessage;
        this.kafkaProducer = kafkaProducer;
        this.taskId = taskMessage.getTaskId();
        this.running = new AtomicBoolean(true);
        this.stopped = new AtomicBoolean(false);
    }
    
    @Override
    public void run() {
        String threadName = "MigrationAgentThread-" + taskId;
        Thread.currentThread().setName(threadName);
        logger.info("[{}] 开始执行增量同步任务", threadName);
        
        try {
            sendStatus("RUNNING", "开始增量同步任务", 0);
            
            if (!startBinlogProcess()) {
                return;
            }
            
            if (!executeFullMigration()) {
                return;
            }
            
            if (!startIncrementProcess()) {
                return;
            }
            
            logger.info("[{}] 增量同步任务启动完成，进入持续监控模式", threadName);
            
            while (running.get() && !stopped.get()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
        } catch (Exception e) {
            logger.error("[{}] 增量同步任务执行异常", threadName, e);
            sendStatus("FAILED", "任务执行异常: " + e.getMessage(), 0);
        } finally {
            stopAllProcesses();
            logger.info("[{}] 增量同步任务线程结束", threadName);
        }
    }
    
    private boolean startBinlogProcess() {
        String threadName = "MigrationAgentThread-" + taskId;
        logger.info("[{}] 启动 binlog 监控进程", threadName);
        
        try {
            binlogProcess = new ProcessManager(BINLOG_JAR_PATH, "BinlogMain-" + taskId);
            binlogProcess.setTaskId(taskId);
            binlogProcess.start();
            
            Thread.sleep(2000);
            
            if (!binlogProcess.isRunning()) {
                logger.error("[{}] binlog 进程启动失败", threadName);
                sendStatus("FAILED", "binlog 进程启动失败", 0);
                return false;
            }
            
            sendStatus("RUNNING", "binlog 监控已启动", 0);
            
            binlogMonitorThread = new Thread(() -> {
                while (running.get() && binlogProcess != null) {
                    try {
                        Thread.sleep(BINLOG_MONITOR_INTERVAL);
                        
                        if (!running.get() || stopped.get()) {
                            break;
                        }
                        
                        if (!binlogProcess.isRunning()) {
                            logger.error("[{}] binlog 进程异常退出", threadName);
                            sendStatus("FAILED", "binlog 进程异常退出", 0);
                            running.set(false);
                            break;
                        }
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.error("[{}] binlog 监控异常", threadName, e);
                    }
                }
            }, "BinlogMonitor-" + taskId);
            binlogMonitorThread.setDaemon(true);
            binlogMonitorThread.start();
            
            logger.info("[{}] binlog 监控进程启动成功", threadName);
            return true;
            
        } catch (Exception e) {
            logger.error("[{}] 启动 binlog 进程失败", threadName, e);
            sendStatus("FAILED", "启动 binlog 进程失败: " + e.getMessage(), 0);
            return false;
        }
    }
    
    private boolean executeFullMigration() {
        String threadName = "MigrationAgentThread-" + taskId;
        logger.info("[{}] 开始执行全量迁移", threadName);
        
        try {
            fullProcess = new ProcessManager(MIGRATION_FULL_JAR_PATH, "MigrationFull-" + taskId);
            fullProcess.setTaskId(taskId);
            fullProcess.start();
            
            sendStatus("RUNNING", "全量迁移进行中", 0);
            
            int exitCode = fullProcess.waitFor();
            
            if (exitCode == 0) {
                logger.info("[{}] 全量迁移完成", threadName);
                sendStatus("FULL_COMPLETED", "全量迁移完成，准备启动增量同步", 100);
                return true;
            } else {
                logger.error("[{}] 全量迁移失败，退出码: {}", threadName, exitCode);
                sendStatus("FAILED", "全量迁移失败，退出码: " + exitCode, 0);
                running.set(false);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("[{}] 全量迁移执行异常", threadName, e);
            sendStatus("FAILED", "全量迁移执行异常: " + e.getMessage(), 0);
            running.set(false);
            return false;
        }
    }
    
    private boolean startIncrementProcess() {
        String threadName = "MigrationAgentThread-" + taskId;
        logger.info("[{}] 启动增量同步进程", threadName);
        
        try {
            incrementProcess = new ProcessManager(MIGRATION_INCREMENT_JAR_PATH, "MigrationIncrement-" + taskId);
            incrementProcess.setTaskId(taskId);
            incrementProcess.setMainArgs(new String[]{taskId});
            incrementProcess.start();
            
            Thread.sleep(2000);
            
            if (!incrementProcess.isRunning()) {
                logger.error("[{}] 增量同步进程启动失败", threadName);
                sendStatus("FAILED", "增量同步进程启动失败", 100);
                return false;
            }
            
            sendStatus("INCREMENT_RUNNING", "增量同步中", 100);
            
            incrementMonitorThread = new Thread(() -> {
                long lastReportTime = System.currentTimeMillis();
                
                while (running.get() && incrementProcess != null) {
                    try {
                        Thread.sleep(INCREMENT_MONITOR_INTERVAL);
                        
                        if (!running.get() || stopped.get()) {
                            break;
                        }
                        
                        if (!incrementProcess.isRunning()) {
                            logger.error("[{}] 增量同步进程异常退出", threadName);
                            sendStatus("FAILED", "增量同步进程异常退出", 100);
                            running.set(false);
                            break;
                        }
                        
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastReportTime >= STATUS_REPORT_INTERVAL) {
                            sendStatus("INCREMENT_RUNNING", "增量同步中", 100);
                            lastReportTime = currentTime;
                            logger.debug("[{}] 增量同步状态已上报", threadName);
                        }
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.error("[{}] 增量同步监控异常", threadName, e);
                    }
                }
            }, "IncrementMonitor-" + taskId);
            incrementMonitorThread.setDaemon(true);
            incrementMonitorThread.start();
            
            logger.info("[{}] 增量同步进程启动成功", threadName);
            return true;
            
        } catch (Exception e) {
            logger.error("[{}] 启动增量同步进程失败", threadName, e);
            sendStatus("FAILED", "启动增量同步进程失败: " + e.getMessage(), 100);
            return false;
        }
    }
    
    public void stop() {
        String threadName = "MigrationAgentThread-" + taskId;
        logger.info("[{}] 收到停止信号", threadName);
        
        stopped.set(true);
        running.set(false);
        
        stopAllProcesses();
    }
    
    private void stopAllProcesses() {
        String threadName = "MigrationAgentThread-" + taskId;
        logger.info("[{}] 停止所有进程", threadName);
        
        if (incrementProcess != null) {
            try {
                incrementProcess.stop();
                logger.info("[{}] 增量同步进程已停止", threadName);
            } catch (Exception e) {
                logger.error("[{}] 停止增量同步进程失败", threadName, e);
            }
        }
        
        if (fullProcess != null) {
            try {
                fullProcess.stop();
                logger.info("[{}] 全量迁移进程已停止", threadName);
            } catch (Exception e) {
                logger.error("[{}] 停止全量迁移进程失败", threadName, e);
            }
        }
        
        if (binlogProcess != null) {
            try {
                binlogProcess.stop();
                logger.info("[{}] binlog 进程已停止", threadName);
            } catch (Exception e) {
                logger.error("[{}] 停止 binlog 进程失败", threadName, e);
            }
        }
        
        if (binlogMonitorThread != null) {
            binlogMonitorThread.interrupt();
        }
        
        if (incrementMonitorThread != null) {
            incrementMonitorThread.interrupt();
        }
    }
    
    public boolean isRunning() {
        return running.get() && !stopped.get();
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    private void sendStatus(String status, String message, int progress) {
        TaskStatusMessage statusMessage = new TaskStatusMessage();
        statusMessage.setTaskId(taskId);
        statusMessage.setStatus(status);
        statusMessage.setMessage(message);
        statusMessage.setProgress(progress);
        
        kafkaProducer.sendStatus(statusMessage);
    }
}
