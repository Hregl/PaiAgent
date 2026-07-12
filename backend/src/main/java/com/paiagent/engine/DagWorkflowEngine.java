package com.paiagent.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * DAG-based workflow engine using Kahn's topological sort algorithm.
 * Nodes at the same topological level are executed in parallel.
 */
@Component
public class DagWorkflowEngine implements WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(DagWorkflowEngine.class);

    private final NodeExecutorFactory executorFactory;
    private final ObjectMapper objectMapper;
    private final int maxParallelism;

    public DagWorkflowEngine(NodeExecutorFactory executorFactory, ObjectMapper objectMapper,
                             @Value("${engine.dag.max-parallelism:4}") int maxParallelism) {
        this.executorFactory = executorFactory;
        this.objectMapper = objectMapper;
        this.maxParallelism = Math.max(1, maxParallelism);
    }

    @Override
    public Map<String, Object> execute(String definitionJson, String userInput) throws Exception {
        return executeInternal(definitionJson, userInput, null);
    }

    @Override
    public Map<String, Object> executeWithProgress(String definitionJson, String userInput,
                                                    Consumer<Map<String, Object>> progressCallback) throws Exception {
        return executeInternal(definitionJson, userInput, progressCallback);
    }

    /**
     * Shared execution logic. When progressCallback is null, no progress events are emitted.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeInternal(String definitionJson, String userInput,
                                                 Consumer<Map<String, Object>> progressCallback) throws Exception {
        Map<String, Object> definition = objectMapper.readValue(definitionJson, new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) definition.get("edges");

        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow has no nodes");
        }

        // Reject condition/judge nodes — only supported by LangGraph engine
        for (Map<String, Object> node : nodes) {
            String type = (String) node.get("type");
            if ("condition".equals(type) || "judge".equals(type)) {
                throw new IllegalArgumentException(
                    "判断节点仅在 LangGraph 引擎下可用，请切换到 LangGraph 引擎后重试");
            }
        }

        Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegreeCopy = new HashMap<>();

        for (Map<String, Object> node : nodes) {
            String id = (String) node.get("id");
            nodeMap.put(id, node);
            adjacency.put(id, new ArrayList<>());
            inDegreeCopy.put(id, 0);
        }

        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                String target = (String) edge.get("target");
                if (!adjacency.containsKey(source) || !adjacency.containsKey(target)) {
                    continue;
                }
                adjacency.get(source).add(target);
                inDegreeCopy.put(target, inDegreeCopy.getOrDefault(target, 0) + 1);
            }
        }

        // Compute levels for parallel execution
        List<List<String>> levels = computeLevels(adjacency, new HashMap<>(inDegreeCopy));

        ExecutionContext context = new ExecutionContext();
        // Thread-safe log collection
        Collection<Map<String, Object>> nodeLogs = new ConcurrentLinkedQueue<>();

        ExecutorService executor = Executors.newFixedThreadPool(maxParallelism, r -> {
            Thread t = new Thread(r, "dag-worker");
            t.setDaemon(true);
            return t;
        });

        try {
            for (List<String> level : levels) {
                if (level.size() == 1) {
                    // Single node in this level — execute inline (no overhead)
                    String nodeId = level.get(0);
                    var result = executeSingleNode(nodeId, nodeMap, context, userInput,
                        progressCallback, nodeLogs);
                    if (!result.success) {
                        return result.toErrorResult(nodeLogs);
                    }
                } else {
                    // Multiple nodes — execute in parallel
                    List<CompletableFuture<NodeResult>> futures = level.stream()
                        .map(nodeId -> CompletableFuture.supplyAsync(() ->
                            executeSingleNode(nodeId, nodeMap, context, userInput,
                                progressCallback, nodeLogs), executor))
                        .toList();

                    // Wait for all to complete and check for failures
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                    for (CompletableFuture<NodeResult> future : futures) {
                        NodeResult result = future.get();
                        if (!result.success) {
                            return result.toErrorResult(nodeLogs);
                        }
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodeLogs", new ArrayList<>(nodeLogs));

        for (List<String> level : levels) {
            for (String nodeId : level) {
                Map<String, Object> node = nodeMap.get(nodeId);
                if ("output".equals(node.get("type"))) {
                    Map<String, Object> outputData = context.getNodeOutputs(nodeId);
                    result.put("output", outputData);
                    return result;
                }
            }
        }

        return result;
    }

    /**
     * Execute a single node and return its result.
     */
    @SuppressWarnings("unchecked")
    private NodeResult executeSingleNode(String nodeId,
                                          Map<String, Map<String, Object>> nodeMap,
                                          ExecutionContext context,
                                          String userInput,
                                          Consumer<Map<String, Object>> progressCallback,
                                          Collection<Map<String, Object>> nodeLogs) {
        Map<String, Object> node = nodeMap.get(nodeId);
        String type = (String) node.get("type");
        Map<String, Object> data = new HashMap<>(
            (Map<String, Object>) node.getOrDefault("data", new HashMap<>()));
        String label = (String) data.getOrDefault("label", type);

        if ("input".equals(type)) {
            data.put("_userInput", userInput);
        }

        // Notify: node starting
        if (progressCallback != null) {
            Map<String, Object> running = new HashMap<>();
            running.put("nodeId", nodeId);
            running.put("nodeType", type);
            running.put("label", label);
            running.put("status", "RUNNING");
            running.put("message", progressMessage(type, label));
            progressCallback.accept(running);
        }

        long nodeStart = System.currentTimeMillis();
        try {
            NodeExecutor nodeExecutor = wrapWithRetry(executorFactory.getExecutor(type), data);
            Map<String, Object> output = nodeExecutor.execute(data, context);
            synchronized (context) {
                context.setNodeOutputs(nodeId, output);
            }

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("nodeId", nodeId);
            logEntry.put("nodeType", type);
            logEntry.put("status", "SUCCESS");
            logEntry.put("input", maskSensitiveData(data));
            logEntry.put("output", output);
            logEntry.put("durationMs", System.currentTimeMillis() - nodeStart);
            nodeLogs.add(logEntry);

            if (progressCallback != null) {
                Map<String, Object> success = new HashMap<>();
                success.put("nodeId", nodeId);
                success.put("nodeType", type);
                success.put("label", label);
                success.put("status", "SUCCESS");
                success.put("message", "Completed in " + (System.currentTimeMillis() - nodeStart) + "ms");
                success.put("durationMs", System.currentTimeMillis() - nodeStart);
                // Attach token usage from LLM node outputs
                if (output.containsKey("_tokenUsage")) {
                    success.put("tokenUsage", output.get("_tokenUsage"));
                }
                progressCallback.accept(success);
            }

            return NodeResult.success();
        } catch (Exception e) {
            log.error("Node {} ({}) failed: {}", nodeId, type, e.getMessage());

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("nodeId", nodeId);
            logEntry.put("nodeType", type);
            logEntry.put("status", "FAILED");
            logEntry.put("input", maskSensitiveData(data));
            logEntry.put("error", e.getMessage());
            logEntry.put("durationMs", System.currentTimeMillis() - nodeStart);
            nodeLogs.add(logEntry);

            if (progressCallback != null) {
                Map<String, Object> failure = new HashMap<>();
                failure.put("nodeId", nodeId);
                failure.put("nodeType", type);
                failure.put("label", label);
                failure.put("status", "FAILED");
                failure.put("message", "Failed: " + e.getMessage());
                failure.put("durationMs", System.currentTimeMillis() - nodeStart);
                progressCallback.accept(failure);
            }

            return NodeResult.failure(nodeId, type, e.getMessage());
        }
    }

    /**
     * Compute topological levels for parallel execution.
     * Level 0 = source nodes (in-degree 0).
     * Level n = nodes whose dependencies are all in levels < n.
     */
    private List<List<String>> computeLevels(Map<String, List<String>> adjacency,
                                              Map<String, Integer> inDegree) {
        List<List<String>> levels = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();

        // Level 0: all source nodes
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        Map<String, Integer> nodeLevel = new HashMap<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            int level = nodeLevel.getOrDefault(node, 0);

            // Grow levels list as needed
            while (levels.size() <= level) {
                levels.add(new ArrayList<>());
            }
            levels.get(level).add(node);

            for (String neighbor : adjacency.getOrDefault(node, Collections.emptyList())) {
                int newInDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newInDegree);
                // Track the longest path level for this neighbor
                nodeLevel.put(neighbor, Math.max(nodeLevel.getOrDefault(neighbor, 0), level + 1));
                if (newInDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        int totalNodes = levels.stream().mapToInt(List::size).sum();
        if (totalNodes != adjacency.size()) {
            throw new IllegalArgumentException(
                "Workflow contains a cycle — " + totalNodes + " of " + adjacency.size() + " nodes processed");
        }

        log.info("DAG levels: {} levels ({} nodes), max parallelism={}",
            levels.size(), totalNodes, maxParallelism);
        for (int i = 0; i < levels.size(); i++) {
            log.debug("  Level {}: {} nodes — {}", i, levels.get(i).size(), levels.get(i));
        }

        return levels;
    }

    private String progressMessage(String type, String label) {
        return switch (type) {
            case "llm" -> "Calling LLM (" + label + ")...";
            case "tts" -> "Synthesizing audio (" + label + ")...";
            case "input" -> "Processing user input...";
            case "output" -> "Assembling output...";
            default -> "Executing " + label + "...";
        };
    }

    private NodeExecutor wrapWithRetry(NodeExecutor executor, Map<String, Object> nodeData) {
        RetryConfig retryConfig = RetryableExecutor.fromNodeData(nodeData);
        if (retryConfig != null) {
            return new RetryableExecutor(executor, retryConfig);
        }
        return executor;
    }

    private Map<String, Object> maskSensitiveData(Map<String, Object> data) {
        Map<String, Object> masked = new HashMap<>(data);
        masked.remove("_userInput");
        if (masked.containsKey("apiKey") && masked.get("apiKey") != null
                && !masked.get("apiKey").toString().isBlank()) {
            masked.put("apiKey", "***");
        }
        return masked;
    }

    /**
     * Result of executing a single node, used for parallel execution orchestration.
     */
    private static class NodeResult {
        final boolean success;
        final String nodeId;
        final String type;
        final String error;

        NodeResult(boolean success, String nodeId, String type, String error) {
            this.success = success;
            this.nodeId = nodeId;
            this.type = type;
            this.error = error;
        }

        static NodeResult success() {
            return new NodeResult(true, null, null, null);
        }

        static NodeResult failure(String nodeId, String type, String error) {
            return new NodeResult(false, nodeId, type, error);
        }

        Map<String, Object> toErrorResult(Collection<Map<String, Object>> nodeLogs) {
            Map<String, Object> result = new HashMap<>();
            result.put("nodeLogs", new ArrayList<>(nodeLogs));
            result.put("status", "FAILED");
            result.put("error", "Node " + nodeId + " (" + type + ") failed: " + error);
            return result;
        }
    }
}
