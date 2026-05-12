package com.paiagent.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * LangGraph4j-based workflow engine for AI agent orchestration.
 * Supports cyclic graphs and agent-style state management.
 */
@Component
public class LangGraphWorkflowEngine implements WorkflowEngine {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LangGraphWorkflowEngine.class);

    private final NodeExecutorFactory executorFactory;
    private final ObjectMapper objectMapper;

    public LangGraphWorkflowEngine(NodeExecutorFactory executorFactory, ObjectMapper objectMapper) {
        this.executorFactory = executorFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(String definitionJson, String userInput) throws Exception {
        return executeInternal(definitionJson, userInput, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeWithProgress(String definitionJson, String userInput,
                                                     Consumer<Map<String, Object>> progressCallback) throws Exception {
        return executeInternal(definitionJson, userInput, progressCallback);
    }

    /**
     * Shared execution logic. When progressCallback is non-null, per-node progress events
     * are emitted in real time (RUNNING → SUCCESS/FAILED).
     */
    private Map<String, Object> executeInternal(String definitionJson, String userInput,
                                                 Consumer<Map<String, Object>> progressCallback) throws Exception {
        Map<String, Object> definition = objectMapper.readValue(definitionJson, new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) definition.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) definition.get("edges");

        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow has no nodes");
        }

        String entryNodeId = findEntryNodeId(nodes, edges);

        // Build node lookup map for label resolution
        Map<String, Map<String, Object>> nodeMap = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            nodeMap.put((String) node.get("id"), node);
        }

        // Thread-safe log collector for node execution records
        List<Map<String, Object>> nodeLogs = Collections.synchronizedList(new ArrayList<>());

        // Build LangGraph StateGraph
        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

        for (Map<String, Object> node : nodes) {
            final String nodeId = (String) node.get("id");
            final String type = (String) node.get("type");
            Map<String, Object> nodeData = (Map<String, Object>) node.getOrDefault("data", new HashMap<>());
            nodeData = new HashMap<>(nodeData); // defensive copy

            if ("input".equals(type)) {
                nodeData.put("_userInput", userInput);
            }

            final String label = (String) nodeData.getOrDefault("label", type);
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

                // Notify: node RUNNING
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
                    ExecutionContext ctx = stateToContext(stateMap);
                    NodeExecutor executor = executorFactory.getExecutor(type);
                    Map<String, Object> output = executor.execute(data, ctx);
                    long duration = System.currentTimeMillis() - nodeStart;

                    // Build node log
                    Map<String, Object> log = new HashMap<>();
                    log.put("nodeId", nodeId);
                    log.put("nodeType", type);
                    log.put("status", "SUCCESS");
                    log.put("output", output);
                    log.put("durationMs", duration);
                    nodeLogs.add(log);

                    // Notify: node SUCCESS
                    if (progressCallback != null) {
                        Map<String, Object> success = new HashMap<>();
                        success.put("nodeId", nodeId);
                        success.put("nodeType", type);
                        success.put("label", label);
                        success.put("status", "SUCCESS");
                        success.put("message", "Completed in " + duration + "ms");
                        success.put("durationMs", duration);
                        progressCallback.accept(success);
                    }

                    // Merge output into state
                    Map<String, Object> updated = new HashMap<>(stateMap);
                    updated.put(nodeId, output);
                    return updated;
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - nodeStart;

                    Map<String, Object> log = new HashMap<>();
                    log.put("nodeId", nodeId);
                    log.put("nodeType", type);
                    log.put("status", "FAILED");
                    log.put("error", e.getMessage());
                    log.put("durationMs", duration);
                    nodeLogs.add(log);

                    // Notify: node FAILED
                    if (progressCallback != null) {
                        Map<String, Object> failed = new HashMap<>();
                        failed.put("nodeId", nodeId);
                        failed.put("nodeType", type);
                        failed.put("label", label);
                        failed.put("status", "FAILED");
                        failed.put("message", "Failed: " + e.getMessage());
                        failed.put("durationMs", duration);
                        progressCallback.accept(failed);
                    }

                    throw new RuntimeException("Node execution failed: " + nodeId + " - " + e.getMessage(), e);
                }
            }));
        }

        // Collect condition node IDs for special edge handling
        Set<String> conditionNodeIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            if ("condition".equals(node.get("type"))) {
                conditionNodeIds.add((String) node.get("id"));
            }
        }

        // Add edges — condition nodes use addConditionalEdges, others use addEdge
        if (edges != null) {
            // Group condition edges by source
            Map<String, Map<String, String>> conditionPathMaps = new HashMap<>();
            for (Map<String, Object> edge : edges) {
                String source = (String) edge.get("source");
                String target = (String) edge.get("target");
                if (conditionNodeIds.contains(source)) {
                    String branch = (String) edge.getOrDefault("branch", "true");
                    conditionPathMaps.computeIfAbsent(source, k -> new LinkedHashMap<>()).put(branch, target);
                } else {
                    graph.addEdge(source, target);
                }
            }

            // Register conditional edges for each condition node
            for (Map.Entry<String, Map<String, String>> entry : conditionPathMaps.entrySet()) {
                String condNodeId = entry.getKey();
                Map<String, String> pathMap = entry.getValue();
                // Ensure both "true" and "false" have fallback targets
                if (!pathMap.containsKey("true")) {
                    pathMap.put("true", StateGraph.END);
                }
                if (!pathMap.containsKey("false")) {
                    pathMap.put("false", StateGraph.END);
                }
                log.info("Condition node {} pathMap: {}", condNodeId, pathMap);

                AsyncEdgeAction<AgentState> conditionAction = state -> {
                    Map<String, Object> data = state.data();
                    Object condOutput = data.get(condNodeId);
                    if (condOutput instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> outputMap = (Map<String, Object>) condOutput;
                        String branch = (String) outputMap.getOrDefault("branch", "false");
                        return java.util.concurrent.CompletableFuture.completedFuture(branch);
                    }
                    return java.util.concurrent.CompletableFuture.completedFuture("false");
                };
                graph.addConditionalEdges(condNodeId, conditionAction, pathMap);
            }
        }

        // Set entry point
        graph.addEdge(StateGraph.START, entryNodeId);

        // Set finish points (output nodes connect to END; condition nodes are skipped)
        for (Map<String, Object> node : nodes) {
            String nodeType = (String) node.get("type");
            if ("output".equals(nodeType)) {
                graph.addEdge((String) node.get("id"), StateGraph.END);
            }
        }

        // Compile and invoke
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("_userInput", userInput);

        var compiled = graph.compile();
        var resultState = compiled.invoke(initialState);

        // Convert result with collected nodeLogs
        return convertToResult(resultState.orElse(new AgentState(initialState)), nodeLogs);
    }

    private String findEntryNodeId(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
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
        log.info("stateToContext: available node outputs = {}", ctx.getAllOutputs().keySet());
        return ctx;
    }

    private String resolveFromState(String template, Map<String, Object> state) {
        String result = template;
        int pos = 0;
        while (true) {
            int start = result.indexOf("{{", pos);
            if (start == -1) break;
            int end = result.indexOf("}}", start);
            if (end == -1) break;

            String ref = result.substring(start + 2, end).trim();
            String[] parts = ref.split("\\.", 2);

            // Only resolve nodeId.field references (those with a dot).
            // Simple {{key}} placeholders are left untouched for downstream
            // executors (e.g. OutputNodeExecutor) to handle.
            if (parts.length == 2) {
                String value = "";
                Object nodeOutput = state.get(parts[0]);
                if (nodeOutput instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> outputMap = (Map<String, Object>) nodeOutput;
                    Object resolved = outputMap.get(parts[1]);
                    value = resolved != null ? resolved.toString() : "";
                }
                result = result.substring(0, start) + value + result.substring(end + 2);
                pos = start + value.length();
            } else {
                // No dot — skip this placeholder (leave as-is)
                pos = end + 2;
            }
        }
        return result;
    }

    private Map<String, Object> convertToResult(AgentState state, List<Map<String, Object>> nodeLogs) {
        Map<String, Object> stateMap = state.data();
        Map<String, Object> result = new HashMap<>();

        result.put("nodeLogs", nodeLogs);

        // Extract output from the output node (prefer entry with "text" key,
        // as TTS nodes also have "audioUrl" and would be picked incorrectly)
        Map<String, Object> bestOutput = null;
        for (Map.Entry<String, Object> entry : stateMap.entrySet()) {
            if (entry.getValue() instanceof Map && !"_userInput".equals(entry.getKey())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) entry.getValue();
                if (data.containsKey("text")) {
                    // Output node — explicitly prefer this
                    bestOutput = data;
                    break;
                }
                if (bestOutput == null && data.containsKey("audioUrl")) {
                    bestOutput = data;
                }
            }
        }
        if (bestOutput != null) {
            result.put("output", bestOutput);
        }

        return result;
    }

    private String progressMessage(String type, String label) {
        switch (type) {
            case "llm": return "Calling LLM (" + label + ")...";
            case "tts": return "Synthesizing audio (" + label + ")...";
            case "input": return "Processing user input...";
            case "output": return "Assembling output...";
            case "condition": return "Evaluating condition (" + label + ")...";
            default: return "Executing " + label + "...";
        }
    }
}
