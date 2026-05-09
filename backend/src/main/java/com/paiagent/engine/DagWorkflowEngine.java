package com.paiagent.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * DAG-based workflow engine using Kahn's topological sort algorithm.
 */
@Component
@ConditionalOnProperty(name = "engine.type", havingValue = "dag", matchIfMissing = true)
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
                throw e;
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
