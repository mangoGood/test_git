package com.synctask.controller;

import com.synctask.dto.ApiResponse;
import com.synctask.entity.ValidationTask;
import com.synctask.entity.Workflow;
import com.synctask.service.ValidationTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/validation-tasks")
public class ValidationTaskController {

    @Autowired
    private ValidationTaskService validationTaskService;

    @GetMapping("/available-workflows")
    public ResponseEntity<?> getAvailableWorkflows(Authentication authentication) {
        try {
            Long userId = getUserId(authentication);
            List<Workflow> workflows = validationTaskService.getIncrementalWorkflows(userId);
            
            List<Map<String, Object>> result = workflows.stream().map(w -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", w.getId());
                map.put("name", w.getName());
                map.put("status", w.getStatus().name());
                map.put("createdAt", w.getCreatedAt());
                return map;
            }).collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of("success", true, "data", result));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getValidationTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        try {
            Long userId = getUserId(authentication);
            Page<ValidationTask> tasks = validationTaskService.getValidationTasks(userId, page, size);
            
            List<Map<String, Object>> content = tasks.getContent().stream().map(this::toMap).collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", content);
            response.put("totalElements", tasks.getTotalElements());
            response.put("totalPages", tasks.getTotalPages());
            response.put("currentPage", page);
            
            return ResponseEntity.ok(Map.of("success", true, "data", response));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getValidationTask(@PathVariable String id, Authentication authentication) {
        try {
            Long userId = getUserId(authentication);
            ValidationTask task = validationTaskService.getValidationTask(id, userId);
            return ResponseEntity.ok(Map.of("success", true, "data", toMap(task)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createValidationTask(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            Long userId = getUserId(authentication);
            String workflowId = request.get("workflowId");
            
            if (workflowId == null || workflowId.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", false, "message", "请选择同步任务"));
            }
            
            ValidationTask task = validationTaskService.createValidationTask(workflowId, userId);
            return ResponseEntity.ok(Map.of("success", true, "data", toMap(task)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteValidationTask(@PathVariable String id, Authentication authentication) {
        try {
            Long userId = getUserId(authentication);
            validationTaskService.deleteValidationTask(id, userId);
            return ResponseEntity.ok(new ApiResponse(true, "校验任务已删除"));
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(false, e.getMessage()));
        }
    }

    private Long getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof com.synctask.security.UserPrincipal) {
            return ((com.synctask.security.UserPrincipal) authentication.getPrincipal()).getId();
        }
        throw new RuntimeException("未授权");
    }

    private Map<String, Object> toMap(ValidationTask task) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", task.getId());
        map.put("name", task.getName());
        map.put("workflowId", task.getWorkflowId());
        map.put("workflowName", task.getWorkflowName());
        map.put("status", task.getStatus().name());
        map.put("totalTables", task.getTotalTables());
        map.put("passedTables", task.getPassedTables());
        map.put("failedTables", task.getFailedTables());
        map.put("totalRows", task.getTotalRows());
        map.put("mismatchedRows", task.getMismatchedRows());
        map.put("errorMessage", task.getErrorMessage());
        map.put("createdAt", task.getCreatedAt());
        map.put("startedAt", task.getStartedAt());
        map.put("completedAt", task.getCompletedAt());
        return map;
    }
}
