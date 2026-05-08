package com.paiagent.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LangGraph4j-based workflow engine for AI agent orchestration.
 * Supports cyclic graphs and agent-style state management.
 */
@Component
@ConditionalOnProperty(name = "engine.type", havingValue = "langgraph")
public class LangGraphWorkflowEngine implements WorkflowEngine {

    private final NodeExecutorFactory executorFactory;
    private final ObjectMapper objectMapper;

    public LangGraphWorkflowEngine(NodeExecutorFactory executorFactory, ObjectMapper objectMapper) {
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

        String entryNodeId = findEntryNodeId(nodes, edges);

        // Build LangGraph StateGraph
        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            String type = (String) node.get("type");
            Map<String, Object> nodeData = (Map<String, Object>) node.getOrDefault("data", new HashMap<>());
            nodeData = new HashMap<>(nodeData); // defensive copy

            if ("input".equals(type)) {
                nodeData.put("_userInput", userInput);
            }

            final Map<String, Object> finalData = nodeData;
            graph.addNode(nodeId, AsyncNodeAction.node_async(state -> {
                Map<String, Object> data = new HashMap<>(finalData);
                Map<String, Object> stateMap = state.data();

                // Resolve template references from state
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (entry.getValue() instanceof String) {
                        String value = (String) entry.getValue();
                        if (value.contains("{{") && value.contains("}}")) {
                            data.put(entry.getKey(), resolveFromState(value, stateMap));
                        }
                    }
                }

                try {
                    ExecutionContext ctx = stateToContext(stateMap);
                    NodeExecutor executor = executorFactory.getExecutor(type);
                    Map<String, Object> output = executor.execute(data, ctx);

                    // Merge output into state
                    Map<String, Object> updated = new HashMap<>(stateMap);
                    updated.put(nodeId, output);
                    return updated;
                } catch (Exception e) {
                    throw new RuntimeException("Node execution failed: " + nodeId + " - " + e.getMessage(), e);
                }
            }));
        }

        // Add edges
        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                String target = (String) edge.get("target");
                graph.addEdge(source, target);
            }
        }

        // Set entry point
        graph.addEdge(StateGraph.START, entryNodeId);

        // Set finish points (output nodes connect to END)
        for (Map<String, Object> node : nodes) {
            if ("output".equals(node.get("type"))) {
                graph.addEdge((String) node.get("id"), StateGraph.END);
            }
        }

        // Compile and invoke
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("_userInput", userInput);

        var compiled = graph.compile();
        var resultState = compiled.invoke(initialState);

        // Convert result to frontend-compatible format
        return convertToResult(resultState.orElse(new AgentState(initialState)));
    }

    private String findEntryNodeId(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        // Find node with no incoming edges (input node or first node)
        if (edges == null || edges.isEmpty()) {
            return (String) nodes.get(0).get("id");
        }

        Set<String> targets = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            targets.add((String) edge.get("target"));
        }

        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            if (!targets.contains(nodeId)) {
                return nodeId;
            }
        }

        return (String) nodes.get(0).get("id");
    }

    private ExecutionContext stateToContext(Map<String, Object> stateMap) {
        ExecutionContext ctx = new ExecutionContext();
        for (Map.Entry<String, Object> entry : stateMap.entrySet()) {
            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nodeOutput = (Map<String, Object>) entry.getValue();
                Map<String, Object> converted = new HashMap<>();
                for (Map.Entry<String, Object> e : nodeOutput.entrySet()) {
                    converted.put(e.getKey(), e.getValue());
                }
                ctx.setNodeOutputs(entry.getKey(), converted);
            }
        }
        return ctx;
    }

    private String resolveFromState(String template, Map<String, Object> state) {
        String result = template;
        while (result.contains("{{") && result.contains("}}")) {
            int start = result.indexOf("{{");
            int end = result.indexOf("}}", start);
            if (end == -1) break;

            String ref = result.substring(start + 2, end).trim();
            String[] parts = ref.split("\\.", 2);
            String value = "";

            if (parts.length == 2) {
                Object nodeOutput = state.get(parts[0]);
                if (nodeOutput instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> outputMap = (Map<String, Object>) nodeOutput;
                    Object resolved = outputMap.get(parts[1]);
                    value = resolved != null ? resolved.toString() : "";
                }
            }

            result = result.substring(0, start) + value + result.substring(end + 2);
        }
        return result;
    }

    private Map<String, Object> convertToResult(AgentState state) {
        Map<String, Object> stateMap = state.data();
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> nodeLogs = new ArrayList<>();

        // Build node logs from state
        for (Map.Entry<String, Object> entry : stateMap.entrySet()) {
            if (entry.getValue() instanceof Map) {
                Map<String, Object> nodeLog = new HashMap<>();
                nodeLog.put("nodeId", entry.getKey());
                nodeLog.put("status", "SUCCESS");
                nodeLog.put("output", entry.getValue());
                nodeLogs.add(nodeLog);
            }
        }

        result.put("nodeLogs", nodeLogs);

        // Extract output from output-like nodes
        for (Map.Entry<String, Object> entry : stateMap.entrySet()) {
            if (entry.getValue() instanceof Map && !"_userInput".equals(entry.getKey())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> outputData = (Map<String, Object>) entry.getValue();
                if (outputData.containsKey("text") || outputData.containsKey("audioUrl")) {
                    result.put("output", outputData);
                    break;
                }
            }
        }

        return result;
    }
}
