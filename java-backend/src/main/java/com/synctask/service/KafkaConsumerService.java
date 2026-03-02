package com.synctask.service;

import com.synctask.dto.TaskStatusMessage;
import com.synctask.dto.TaskStatusUpdate;
import com.synctask.entity.Workflow;
import com.synctask.entity.WorkflowLog;
import com.synctask.entity.WorkflowStatus;
import com.synctask.repository.WorkflowLogRepository;
import com.synctask.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowLogRepository workflowLogRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "${spring.kafka.topics.task-status}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeTaskStatusMessage(TaskStatusMessage message) {
        logger.info("收到任务状态消息: taskId={}, status={}, progress={}", 
            message.getTaskId(), message.getStatus(), message.getProgress());

        try {
            Workflow workflow = workflowRepository.findById(message.getTaskId()).orElse(null);
            if (workflow == null) {
                logger.warn("任务不存在: taskId={}", message.getTaskId());
                return;
            }

            WorkflowStatus newStatus = WorkflowStatus.valueOf(message.getStatus().toUpperCase());
            WorkflowStatus oldStatus = workflow.getStatus();
            
            workflow.setStatus(newStatus);

            if (message.getProgress() != null) {
                workflow.setProgress(message.getProgress());
            }

            if (message.getErrorMessage() != null) {
                workflow.setErrorMessage(message.getErrorMessage());
            }

            if (message.getIsBilling() != null) {
                workflow.setIsBilling(message.getIsBilling());
            }

            if (newStatus == WorkflowStatus.COMPLETED || newStatus == WorkflowStatus.FAILED) {
                workflow.setCompletedAt(LocalDateTime.now());
            }

            workflowRepository.save(workflow);
            logger.info("任务状态已更新: taskId={}, status={}", workflow.getId(), workflow.getStatus());

            String logMessage = buildStatusLogMessage(newStatus, oldStatus, message);
            WorkflowLog.LogLevel logLevel = determineLogLevel(newStatus);
            
            addLog(workflow.getId(), logLevel, logMessage);

            TaskStatusUpdate update = new TaskStatusUpdate();
            update.setTaskId(workflow.getId());
            update.setStatus(workflow.getStatus().name());
            update.setProgress(workflow.getProgress());
            update.setUserId(workflow.getUserId());

            messagingTemplate.convertAndSend("/topic/task-status", update);
            messagingTemplate.convertAndSendToUser(
                workflow.getUserId().toString(), 
                "/queue/task-status", 
                update
            );
            logger.info("任务状态更新已推送到 WebSocket: taskId={}, userId={}", workflow.getId(), workflow.getUserId());

        } catch (Exception e) {
            logger.error("处理任务状态消息失败: taskId={}", message.getTaskId(), e);
        }
    }

    private String buildStatusLogMessage(WorkflowStatus newStatus, WorkflowStatus oldStatus, TaskStatusMessage message) {
        switch (newStatus) {
            case RUNNING:
                if (message.getProgress() != null && message.getProgress() > 0) {
                    return String.format("任务执行中，进度: %d%%", message.getProgress());
                }
                return "任务开始执行";
            case COMPLETED:
                return "任务执行完成";
            case FAILED:
                String errorMsg = message.getErrorMessage();
                return errorMsg != null && !errorMsg.isEmpty() 
                    ? String.format("任务执行失败: %s", errorMsg)
                    : "任务执行失败";
            case PAUSED:
                return "任务已暂停";
            default:
                return String.format("任务状态更新为: %s", newStatus.name());
        }
    }

    private WorkflowLog.LogLevel determineLogLevel(WorkflowStatus status) {
        switch (status) {
            case FAILED:
                return WorkflowLog.LogLevel.ERROR;
            case PAUSED:
                return WorkflowLog.LogLevel.WARNING;
            default:
                return WorkflowLog.LogLevel.INFO;
        }
    }

    private void addLog(String workflowId, WorkflowLog.LogLevel level, String message) {
        WorkflowLog log = new WorkflowLog();
        log.setWorkflowId(workflowId);
        log.setLevel(level);
        log.setMessage(message);
        workflowLogRepository.save(log);
        logger.info("已添加任务日志: workflowId={}, level={}, message={}", workflowId, level, message);
    }
}
