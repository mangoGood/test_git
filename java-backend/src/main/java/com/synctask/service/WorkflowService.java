package com.synctask.service;

import com.synctask.entity.Workflow;
import com.synctask.entity.WorkflowLog;
import com.synctask.entity.WorkflowStatus;
import com.synctask.repository.WorkflowLogRepository;
import com.synctask.repository.WorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    public Workflow createWorkflow(String name, String sourceConnection, String targetConnection, Long userId) {
        Workflow workflow = new Workflow();
        workflow.setId(UUID.randomUUID().toString());
        workflow.setName(name);
        workflow.setSourceConnection(sourceConnection != null ? sourceConnection : "default-source");
        workflow.setTargetConnection(targetConnection != null ? targetConnection : "default-target");
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

    public Page<Workflow> getWorkflowsByUserId(Long userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        return workflowRepository.findByUserId(userId, pageable);
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
        addLog(workflow.getId(), WorkflowLog.LogLevel.INFO, "任务已暂停，状态: 暂停中");
    }

    @Transactional
    public void resumeWorkflow(String id, Long userId) {
        Workflow workflow = getWorkflowById(id, userId);
        workflow.setStatus(WorkflowStatus.RUNNING);
        workflowRepository.save(workflow);
        addLog(workflow.getId(), WorkflowLog.LogLevel.INFO, "任务已恢复，等待任务执行服务处理");
    }

    @Transactional
    public void deleteWorkflow(String id, Long userId) {
        Workflow workflow = getWorkflowById(id, userId);
        workflowRepository.delete(workflow);
    }

    private void addLog(String workflowId, WorkflowLog.LogLevel level, String message) {
        WorkflowLog log = new WorkflowLog();
        log.setWorkflowId(workflowId);
        log.setLevel(level);
        log.setMessage(message);
        workflowLogRepository.save(log);
    }
}
