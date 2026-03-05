package com.synctask.service;

import com.synctask.dto.TaskCreatedMessage;
import com.synctask.entity.Workflow;
import com.synctask.entity.WorkflowLog;
import com.synctask.entity.WorkflowStatus;
import com.synctask.repository.WorkflowLogRepository;
import com.synctask.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {
    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowLogRepository workflowLogRepository;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Transactional
    public Workflow createWorkflow(String name, String sourceConnection, String targetConnection, String migrationMode, Long userId) {
        Workflow workflow = new Workflow();
        workflow.setId(UUID.randomUUID().toString());
        workflow.setName(name);
        workflow.setSourceConnection(sourceConnection != null ? sourceConnection : "default-source");
        workflow.setTargetConnection(targetConnection != null ? targetConnection : "default-target");
        workflow.setMigrationMode(migrationMode != null ? migrationMode : "full");
        workflow.setStatus(WorkflowStatus.PENDING);
        workflow.setUserId(userId);
        workflow.setProgress(0);
        workflow.setIsBilling(true);

        Workflow savedWorkflow = workflowRepository.save(workflow);
        
        addLog(savedWorkflow.getId(), WorkflowLog.LogLevel.INFO, "任务创建成功，状态: 创建中");
        
        try {
            kafkaProducerService.sendTaskCreatedMessage(savedWorkflow);
            addLog(savedWorkflow.getId(), WorkflowLog.LogLevel.INFO, "任务消息已发送到 Kafka topic: sync-task-created，等待任务执行服务处理");
        } catch (Exception e) {
            addLog(savedWorkflow.getId(), WorkflowLog.LogLevel.WARNING, "Kafka 消息发送失败: " + e.getMessage());
        }
        
        return savedWorkflow;
    }

    public Page<Workflow> getWorkflowsByUserId(Long userId, int page, int pageSize, String sortBy, String sortDirection) {
        // 将前端字段名映射为 JPA 实体字段名
        String fieldName = mapSortField(sortBy);
        
        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, fieldName);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        return workflowRepository.findByUserId(userId, pageable);
    }
    
    private String mapSortField(String sortBy) {
        switch (sortBy) {
            case "name":
                return "name";
            case "status":
                return "status";
            case "created_at":
                return "createdAt";
            case "is_billing":
                return "isBilling";
            default:
                return "createdAt";
        }
    }

    public Workflow getWorkflowById(String id, Long userId) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("任务不存在"));
        
        if (!workflow.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问此任务");
        }
        
        return workflow;
    }

    public List<WorkflowLog> getWorkflowLogs(String workflowId, Long userId) {
        Workflow workflow = getWorkflowById(workflowId, userId);
        return workflowLogRepository.findByWorkflowIdOrderByCreatedAtDesc(workflow.getId());
    }

    @Transactional
    public void pauseWorkflow(String id, Long userId) {
        Workflow workflow = getWorkflowById(id, userId);
        workflow.setStatus(WorkflowStatus.PAUSED);
        workflowRepository.save(workflow);
        
        TaskCreatedMessage message = new TaskCreatedMessage();
        message.setTaskId(workflow.getId());
        message.setTaskName(workflow.getName());
        message.setUserId(workflow.getUserId());
        message.setSourceConnection(workflow.getSourceConnection());
        message.setTargetConnection(workflow.getTargetConnection());
        message.setMigrationMode(workflow.getMigrationMode());
        message.setCreatedAt(workflow.getCreatedAt());
        message.setMessageType("stop");
        
        kafkaProducerService.sendControlMessage(message);
        addLog(workflow.getId(), WorkflowLog.LogLevel.INFO, "任务已暂停，发送停止消息到 Kafka");
    }

    @Transactional
    public void resumeWorkflow(String id, Long userId) {
        Workflow workflow = getWorkflowById(id, userId);
        workflow.setStatus(WorkflowStatus.RUNNING);
        workflowRepository.save(workflow);
        
        TaskCreatedMessage message = new TaskCreatedMessage();
        message.setTaskId(workflow.getId());
        message.setTaskName(workflow.getName());
        message.setUserId(workflow.getUserId());
        message.setSourceConnection(workflow.getSourceConnection());
        message.setTargetConnection(workflow.getTargetConnection());
        message.setMigrationMode(workflow.getMigrationMode());
        message.setCreatedAt(workflow.getCreatedAt());
        message.setMessageType("resume");
        
        kafkaProducerService.sendControlMessage(message);
        addLog(workflow.getId(), WorkflowLog.LogLevel.INFO, "任务已恢复，发送恢复消息到 Kafka，等待任务执行服务处理");
    }

    @Transactional
    public void deleteWorkflow(String id, Long userId) {
        Workflow workflow = getWorkflowById(id, userId);
        
        TaskCreatedMessage message = new TaskCreatedMessage();
        message.setTaskId(workflow.getId());
        message.setTaskName(workflow.getName());
        message.setUserId(workflow.getUserId());
        message.setSourceConnection(workflow.getSourceConnection());
        message.setTargetConnection(workflow.getTargetConnection());
        message.setMigrationMode(workflow.getMigrationMode());
        message.setCreatedAt(workflow.getCreatedAt());
        message.setMessageType("delete");
        
        try {
            kafkaProducerService.sendControlMessage(message);
            addLog(workflow.getId(), WorkflowLog.LogLevel.INFO, "发送删除消息到 Kafka，停止所有相关进程");
        } catch (Exception e) {
            addLog(workflow.getId(), WorkflowLog.LogLevel.WARNING, "Kafka 删除消息发送失败: " + e.getMessage());
        }
        
        workflow.setIsDeleted(true);
        workflowRepository.save(workflow);
        addLog(workflow.getId(), WorkflowLog.LogLevel.INFO, "任务已删除（软删除），状态: 已删除");
    }

    private void addLog(String workflowId, WorkflowLog.LogLevel level, String message) {
        WorkflowLog log = new WorkflowLog();
        log.setWorkflowId(workflowId);
        log.setLevel(level);
        log.setMessage(message);
        workflowLogRepository.save(log);
    }
}
