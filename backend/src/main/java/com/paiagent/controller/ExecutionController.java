package com.paiagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paiagent.engine.DagWorkflowEngine;
import com.paiagent.engine.WorkflowEngine;
import com.paiagent.model.dto.ApiResponse;
import com.paiagent.model.dto.ExecutionRequest;
import com.paiagent.model.entity.ExecutionLog;
import com.paiagent.model.entity.Workflow;
import com.paiagent.repository.ExecutionLogRepository;
import com.paiagent.repository.WorkflowRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api")
public class ExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionController.class);

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

            String execStatus = (String) result.getOrDefault("status", "SUCCESS");

            // Save execution log
            ExecutionLog log = new ExecutionLog();
            log.setId(UUID.randomUUID().toString());
            log.setWorkflowId(id);
            log.setInput(request.getInput());
            log.setStatus(execStatus);
            log.setDurationMs((int) duration);
            log.setCreatedAt(LocalDateTime.now());

            if ("FAILED".equals(execStatus)) {
                // Partial execution — store error in output field
                log.setOutput((String) result.getOrDefault("error", "Unknown error"));
            } else {
                log.setOutput(objectMapper.writeValueAsString(result));
            }
            executionLogRepository.save(log);

            result.put("executionId", log.getId());
            result.put("durationMs", duration);
            // Return code 200 even on partial failure so frontend gets nodeLogs
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

    /**
     * List execution history for a workflow (latest 20).
     */
    @GetMapping("/workflows/{id}/executions")
    public ApiResponse<List<ExecutionLog>> listExecutions(@PathVariable String id) {
        List<ExecutionLog> logs = executionLogRepository.findByWorkflowIdOrderByCreatedAtDesc(id);
        // Limit to last 20 to avoid huge payloads
        if (logs.size() > 20) {
            logs = logs.subList(0, 20);
        }
        return ApiResponse.success(logs);
    }

    /**
     * SSE streaming endpoint — pushes per-node progress events to the frontend.
     */
    @GetMapping("/workflows/{id}/execute-stream")
    public SseEmitter executeStream(@PathVariable String id, @RequestParam String input) {
        Workflow workflow = workflowRepository.findById(id).orElse(null);
        if (workflow == null) {
            SseEmitter errorEmitter = new SseEmitter(0L);
            errorEmitter.completeWithError(new IllegalArgumentException("Workflow not found"));
            return errorEmitter;
        }

        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout
        String defJson = workflow.getDefinition();
        String userInput = input;

        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                if (!(workflowEngine instanceof DagWorkflowEngine)) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", "Progress streaming only supports DAG engine")));
                    emitter.complete();
                    return;
                }

                DagWorkflowEngine dagEngine = (DagWorkflowEngine) workflowEngine;
                Map<String, Object> result = dagEngine.executeWithProgress(defJson, userInput, progress -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(progress));
                    } catch (Exception ignored) {
                    }
                });

                long duration = System.currentTimeMillis() - startTime;
                String execStatus = (String) result.getOrDefault("status", "SUCCESS");

                // Save execution log
                ExecutionLog execLog = new ExecutionLog();
                execLog.setId(UUID.randomUUID().toString());
                execLog.setWorkflowId(id);
                execLog.setInput(userInput);
                execLog.setStatus(execStatus);
                execLog.setDurationMs((int) duration);
                execLog.setCreatedAt(LocalDateTime.now());
                if ("FAILED".equals(execStatus)) {
                    execLog.setOutput((String) result.getOrDefault("error", "Unknown error"));
                } else {
                    execLog.setOutput(objectMapper.writeValueAsString(result));
                }
                executionLogRepository.save(execLog);

                result.put("executionId", execLog.getId());
                result.put("durationMs", duration);

                log.info("SSE result output: {}", result.get("output"));

                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(result));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", e.getMessage())));
                } catch (Exception ignored) {
                }
                emitter.complete();
            }
        });

        return emitter;
    }
}
