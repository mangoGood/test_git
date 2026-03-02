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
import org.springframework.scheduling.annotation.Async;
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
        
        startWorkflowAsync(savedWorkflow.getId());
        
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
        addLog(workflow.getId(), WorkflowLog.LogLevel.INFO, "任务已暂停");
    }

    @Transactional
    public void resumeWorkflow(String id, Long userId) {
        Workflow workflow = getWorkflowById(id, userId);
        workflow.setStatus(WorkflowStatus.RUNNING);
        workflowRepository.save(workflow);
        addLog(workflow.getId(), WorkflowLog.LogLevel.INFO, "任务已恢复");
        startWorkflowAsync(id);
    }

    @Transactional
    public void deleteWorkflow(String id, Long userId) {
        Workflow workflow = getWorkflowById(id, userId);
        workflowRepository.delete(workflow);
    }

    @Async
    protected void startWorkflowAsync(String workflowId) {
        try {
            Thread.sleep(1000);
            
            Workflow workflow = workflowRepository.findById(workflowId).orElse(null);
            if (workflow == null || workflow.getStatus() == WorkflowStatus.PAUSED) {
                return;
            }
            
            workflow.setStatus(WorkflowStatus.RUNNING);
            workflowRepository.save(workflow);
            addLog(workflowId, WorkflowLog.LogLevel.INFO, "任务开始执行");
            
            for (int i = 1; i <= 10; i++) {
                Thread.sleep(500);
                
                workflow = workflowRepository.findById(workflowId).orElse(null);
                if (workflow == null || workflow.getStatus() == WorkflowStatus.PAUSED) {
                    return;
                }
                
                workflow.setProgress(i * 10);
                workflowRepository.save(workflow);
                addLog(workflowId, WorkflowLog.LogLevel.INFO, "任务执行进度: " + (i * 10) + "%");
            }
            
            workflow = workflowRepository.findById(workflowId).orElse(null);
            if (workflow != null && workflow.getStatus() != WorkflowStatus.PAUSED) {
                workflow.setStatus(WorkflowStatus.COMPLETED);
                workflow.setProgress(100);
                workflow.setIsBilling(false);
                workflowRepository.save(workflow);
                addLog(workflowId, WorkflowLog.LogLevel.INFO, "任务执行完成");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Workflow workflow = workflowRepository.findById(workflowId).orElse(null);
            if (workflow != null) {
                workflow.setStatus(WorkflowStatus.FAILED);
                workflow.setErrorMessage(e.getMessage());
                workflow.setIsBilling(false);
                workflowRepository.save(workflow);
                addLog(workflowId, WorkflowLog.LogLevel.ERROR, "任务执行失败: " + e.getMessage());
            }
        }
    }

    private void addLog(String workflowId, WorkflowLog.LogLevel level, String message) {
        WorkflowLog log = new WorkflowLog();
        log.setWorkflowId(workflowId);
        log.setLevel(level);
        log.setMessage(message);
        workflowLogRepository.save(log);
    }
}
