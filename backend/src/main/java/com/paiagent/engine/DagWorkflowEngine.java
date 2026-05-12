package com.paiagent.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * DAG-based workflow engine using Kahn's topological sort algorithm.
 */
@Component
public class DagWorkflowEngine implements WorkflowEngine {

    private final NodeExecutorFactory executorFactory;
    private final ObjectMapper objectMapper;

    public DagWorkflowEngine(NodeExecutorFactory executorFactory, ObjectMapper objectMapper) {
        this.executorFactory = executorFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(String definitionJson, String userInput) throws Exception {
        Map<String, Object> definition = objectMapper.readValue(definitionJson, new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) definition.get("edges");

        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow has no nodes");
        }

        Map<String, Map<String, Object>> nodeMap = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (Map<String, Object> node : nodes) {
            String id = (String) node.get("id");
            nodeMap.put(id, node);
            adjacency.put(id, new ArrayList<>());
            inDegree.put(id, 0);
        }

        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                String target = (String) edge.get("target");
                adjacency.get(source).add(target);
                inDegree.put(target, inDegree.getOrDefault(target, 0) + 1);
            }
        }

        List<String> executionOrder = topologicalSort(adjacency, inDegree);

        ExecutionContext context = new ExecutionContext();
        List<Map<String, Object>> nodeLogs = new ArrayList<>();

        for (String nodeId : executionOrder) {
            Map<String, Object> node = nodeMap.get(nodeId);
            String type = (String) node.get("type");
            Map<String, Object> data = (Map<String, Object>) node.getOrDefault("data", new HashMap<>());

            if ("input".equals(type)) {
                data.put("_userInput", userInput);
            }

            long nodeStart = System.currentTimeMillis();
            try {
                NodeExecutor executor = executorFactory.getExecutor(type);
                Map<String, Object> output = executor.execute(data, context);
                context.setNodeOutputs(nodeId, output);

                Map<String, Object> log = new HashMap<>();
                log.put("nodeId", nodeId);
                log.put("nodeType", type);
                log.put("status", "SUCCESS");
                log.put("input", maskSensitiveData(data));
                log.put("output", output);
                log.put("durationMs", System.currentTimeMillis() - nodeStart);
                nodeLogs.add(log);
            } catch (Exception e) {
                Map<String, Object> log = new HashMap<>();
                log.put("nodeId", nodeId);
                log.put("nodeType", type);
                log.put("status", "FAILED");
                log.put("input", maskSensitiveData(data));
                log.put("error", e.getMessage());
                log.put("durationMs", System.currentTimeMillis() - nodeStart);
                nodeLogs.add(log);

                // Return partial result so debug can show successful nodes
                Map<String, Object> result = new HashMap<>();
                result.put("nodeLogs", nodeLogs);
                result.put("status", "FAILED");
                result.put("error", "Node " + nodeId + " (" + type + ") failed: " + e.getMessage());
                return result;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodeLogs", nodeLogs);

        for (String nodeId : executionOrder) {
            Map<String, Object> node = nodeMap.get(nodeId);
            if ("output".equals(node.get("type"))) {
                Map<String, Object> outputData = context.getNodeOutputs(nodeId);
                result.put("output", outputData);
                break;
            }
        }

        return result;
    }

    /**
     * Execute workflow with a progress callback invoked before/after each node.
     * The callback receives a map with: nodeId, nodeType, label, status (RUNNING/SUCCESS/FAILED), message, durationMs
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeWithProgress(String definitionJson, String userInput,
                                                    Consumer<Map<String, Object>> progressCallback) throws Exception {
        Map<String, Object> definition = objectMapper.readValue(definitionJson, new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) definition.get("edges");

        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow has no nodes");
        }

        Map<String, Map<String, Object>> nodeMap = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (Map<String, Object> node : nodes) {
            String id = (String) node.get("id");
            nodeMap.put(id, node);
            adjacency.put(id, new ArrayList<>());
            inDegree.put(id, 0);
        }

        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                String target = (String) edge.get("target");
                adjacency.get(source).add(target);
                inDegree.put(target, inDegree.getOrDefault(target, 0) + 1);
            }
        }

        List<String> executionOrder = topologicalSort(adjacency, inDegree);

        ExecutionContext context = new ExecutionContext();
        List<Map<String, Object>> nodeLogs = new ArrayList<>();

        for (String nodeId : executionOrder) {
            Map<String, Object> node = nodeMap.get(nodeId);
            String type = (String) node.get("type");
            Map<String, Object> data = (Map<String, Object>) node.getOrDefault("data", new HashMap<>());
            String label = (String) data.getOrDefault("label", type);

            if ("input".equals(type)) {
                data.put("_userInput", userInput);
            }

            // Notify: node starting
            Map<String, Object> progress = new HashMap<>();
            progress.put("nodeId", nodeId);
            progress.put("nodeType", type);
            progress.put("label", label);
            progress.put("status", "RUNNING");
            progress.put("message", progressMessage(type, label));
            progressCallback.accept(progress);

            long nodeStart = System.currentTimeMillis();
            try {
                NodeExecutor executor = executorFactory.getExecutor(type);
                Map<String, Object> output = executor.execute(data, context);
                context.setNodeOutputs(nodeId, output);

                Map<String, Object> log = new HashMap<>();
                log.put("nodeId", nodeId);
                log.put("nodeType", type);
                log.put("status", "SUCCESS");
                log.put("input", maskSensitiveData(data));
                log.put("output", output);
                log.put("durationMs", System.currentTimeMillis() - nodeStart);
                nodeLogs.add(log);

                // Notify: node success
                progress.put("nodeId", nodeId);
                progress.put("nodeType", type);
                progress.put("label", label);
                progress.put("status", "SUCCESS");
                progress.put("message", "Completed in " + (System.currentTimeMillis() - nodeStart) + "ms");
                progress.put("durationMs", System.currentTimeMillis() - nodeStart);
                progressCallback.accept(progress);
            } catch (Exception e) {
                Map<String, Object> log = new HashMap<>();
                log.put("nodeId", nodeId);
                log.put("nodeType", type);
                log.put("status", "FAILED");
                log.put("input", maskSensitiveData(data));
                log.put("error", e.getMessage());
                log.put("durationMs", System.currentTimeMillis() - nodeStart);
                nodeLogs.add(log);

                // Notify: node failure
                progress.put("nodeId", nodeId);
                progress.put("nodeType", type);
                progress.put("label", label);
                progress.put("status", "FAILED");
                progress.put("message", "Failed: " + e.getMessage());
                progress.put("durationMs", System.currentTimeMillis() - nodeStart);
                progressCallback.accept(progress);

                Map<String, Object> result = new HashMap<>();
                result.put("nodeLogs", nodeLogs);
                result.put("status", "FAILED");
                result.put("error", "Node " + nodeId + " (" + type + ") failed: " + e.getMessage());
                return result;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodeLogs", nodeLogs);

        for (String nodeId : executionOrder) {
            Map<String, Object> node = nodeMap.get(nodeId);
            if ("output".equals(node.get("type"))) {
                Map<String, Object> outputData = context.getNodeOutputs(nodeId);
                result.put("output", outputData);
                break;
            }
        }

        return result;
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

    private List<String> topologicalSort(Map<String, List<String>> adjacency, Map<String, Integer> inDegree) {
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            order.add(node);
            for (String neighbor : adjacency.getOrDefault(node, Collections.emptyList())) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (order.size() != adjacency.size()) {
            throw new IllegalArgumentException("Workflow contains a cycle");
        }
        return order;
    }

    /**
     * Create a copy of node data with sensitive fields masked for debug logging.
     */
    private Map<String, Object> maskSensitiveData(Map<String, Object> data) {
        Map<String, Object> masked = new HashMap<>(data);
        masked.remove("_userInput");
        if (masked.containsKey("apiKey") && masked.get("apiKey") != null
                && !masked.get("apiKey").toString().isBlank()) {
            masked.put("apiKey", "***");
        }
        return masked;
    }
}
