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

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
     * SSE streaming endpoint — writes per-node progress events directly to the response
     * with immediate flush, ensuring the frontend sees each node start/finish in real time.
     */
    @GetMapping("/workflows/{id}/execute-stream")
    public void executeStream(@PathVariable String id, @RequestParam String input,
                              HttpServletResponse response) {
        Workflow workflow = workflowRepository.findById(id).orElse(null);

        // Set SSE headers
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");  // Disable nginx buffering
        response.setBufferSize(0);  // Disable Tomcat output buffer — force immediate flush

        try {
            PrintWriter writer = response.getWriter();

            // Send initial comment to establish SSE connection and flush headers
            writer.write(": connected\n\n");
            writer.flush();

            CountDownLatch latch = new CountDownLatch(1);

            if (workflow == null) {
                writeSSE(writer, "error", objectMapper.writeValueAsString(Map.of("message", "工作流不存在")));
                writer.close();
                return;
            }

            if (!(workflowEngine instanceof DagWorkflowEngine)) {
                writeSSE(writer, "error", objectMapper.writeValueAsString(Map.of("message", "仅 DAG 引擎支持进度推送")));
                writer.close();
                return;
            }

            String defJson = workflow.getDefinition();
            String userInput = input;
            DagWorkflowEngine dagEngine = (DagWorkflowEngine) workflowEngine;

            CompletableFuture.runAsync(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    Map<String, Object> result = dagEngine.executeWithProgress(defJson, userInput, progress -> {
                        try {
                            String eventData = objectMapper.writeValueAsString(progress);
                            log.info("SSE progress: nodeId={}, type={}, status={}",
                                    progress.get("nodeId"), progress.get("nodeType"), progress.get("status"));
                            writeSSE(writer, "progress", eventData);
                        } catch (Exception ex) {
                            log.error("SSE progress write failed", ex);
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

                    writeSSE(writer, "result", objectMapper.writeValueAsString(result));
                    log.info("SSE result sent, executionId={}, status={}", result.get("executionId"), execStatus);
                } catch (Exception e) {
                    try {
                        writeSSE(writer, "error", objectMapper.writeValueAsString(Map.of("message", e.getMessage())));
                    } catch (Exception ignored) {
                    }
                } finally {
                    latch.countDown();
                }
            });

            // Keep connection alive until async task completes
            latch.await(5, TimeUnit.MINUTES);
            writer.close();
        } catch (Exception e) {
            log.error("SSE stream error", e);
        }
    }

    /**
     * Write a single SSE event and immediately flush to the client.
     */
    private void writeSSE(PrintWriter writer, String event, String data) {
        writer.write("event: " + event + "\n");
        writer.write("data: " + data + "\n\n");
        writer.flush();
        log.debug("SSE flushed: event={}, len={}", event, data.length());
    }
}
