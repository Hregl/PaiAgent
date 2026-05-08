package com.paiagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paiagent.engine.WorkflowEngine;
import com.paiagent.model.dto.ApiResponse;
import com.paiagent.model.dto.ExecutionRequest;
import com.paiagent.model.entity.ExecutionLog;
import com.paiagent.model.entity.Workflow;
import com.paiagent.repository.ExecutionLogRepository;
import com.paiagent.repository.WorkflowRepository;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ExecutionController {

    private final WorkflowRepository workflowRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    public ExecutionController(WorkflowRepository workflowRepository,
                               ExecutionLogRepository executionLogRepository,
                               WorkflowEngine workflowEngine,
                               ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.executionLogRepository = executionLogRepository;
        this.workflowEngine = workflowEngine;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/workflows/{id}/execute")
    public ApiResponse<Map<String, Object>> execute(@PathVariable String id,
                                                     @Valid @RequestBody ExecutionRequest request) {
        Workflow workflow = workflowRepository.findById(id).orElse(null);
        if (workflow == null) {
            return ApiResponse.error(404, "Workflow not found");
        }

        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> result = workflowEngine.execute(workflow.getDefinition(), request.getInput());
            long duration = System.currentTimeMillis() - startTime;

            // Save execution log
            ExecutionLog log = new ExecutionLog();
            log.setId(UUID.randomUUID().toString());
            log.setWorkflowId(id);
            log.setInput(request.getInput());
            log.setOutput(objectMapper.writeValueAsString(result));
            log.setStatus("SUCCESS");
            log.setDurationMs((int) duration);
            log.setCreatedAt(LocalDateTime.now());
            executionLogRepository.save(log);

            result.put("executionId", log.getId());
            result.put("status", "SUCCESS");
            result.put("durationMs", duration);
            return ApiResponse.success(result);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            ExecutionLog log = new ExecutionLog();
            log.setId(UUID.randomUUID().toString());
            log.setWorkflowId(id);
            log.setInput(request.getInput());
            log.setStatus("FAILED");
            log.setDurationMs((int) duration);
            log.setCreatedAt(LocalDateTime.now());
            try {
                executionLogRepository.save(log);
            } catch (Exception ignored) {
            }

            return ApiResponse.error(500, "Execution failed: " + e.getMessage());
        }
    }

    @GetMapping("/executions/{id}")
    public ApiResponse<ExecutionLog> getExecution(@PathVariable String id) {
        ExecutionLog log = executionLogRepository.findById(id).orElse(null);
        if (log == null) {
            return ApiResponse.error(404, "Execution not found");
        }
        return ApiResponse.success(log);
    }
}
