package com.paiagent.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.CompileConfig;
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

            // Extract phase metadata for progress display
            final Object rawPhaseIndex = nodeData.get("phaseIndex");
            final Object rawTotalPhases = nodeData.get("totalPhases");
            final Integer phaseIndex = rawPhaseIndex instanceof Number ? ((Number) rawPhaseIndex).intValue() : null;
            final Integer totalPhases = rawTotalPhases instanceof Number ? ((Number) rawTotalPhases).intValue() : null;
            final String phasePrefix = (phaseIndex != null && totalPhases != null)
                    ? String.format("阶段 %d/%d: ", phaseIndex + 1, totalPhases)
                    : "";
            
            // Retrieve retry config for judge nodes to prevent infinite loops
            final int maxRetries = nodeData.get("maxRetries") instanceof Number
                    ? ((Number) nodeData.get("maxRetries")).intValue() : 3;
            final String retryKey = "_retry_" + nodeId;

            graph.addNode(nodeId, AsyncNodeAction.node_async(state -> {
                Map<String, Object> data = new HashMap<>(finalData);
                Map<String, Object> stateMap = state.data();

                // Retry cap for judge nodes: if retry count >= maxRetries, force pass
                if ("judge".equals(type)) {
                    int retryCount = stateMap.containsKey(retryKey)
                            ? ((Number) stateMap.get(retryKey)).intValue() : 0;
                    if (retryCount >= maxRetries) {
                        log.warn("Judge {} reached max retries ({}), forcing pass", nodeId, maxRetries);
                        Map<String, Object> forced = new HashMap<>();
                        forced.put("branch", "true");
                        forced.put("reasoning", "达到最大重试次数(" + maxRetries + ")，强制通过");
                        Map<String, Object> updated = new HashMap<>(stateMap);
                        updated.put(nodeId, forced);
                        return updated;
                    }
                    // Inject current retry count into data for the executor
                    data.put("_retryCount", retryCount);
                }

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
                    running.put("message", phasePrefix + progressMessage(type, label));
                    if (phaseIndex != null) running.put("phaseIndex", phaseIndex);
                    if (totalPhases != null) running.put("totalPhases", totalPhases);
                    progressCallback.accept(running);
                }

                long nodeStart = System.currentTimeMillis();
                try {
                    ExecutionContext ctx = stateToContext(stateMap);
                    NodeExecutor rawExecutor = executorFactory.getExecutor(type);
                    NodeExecutor executor = wrapWithRetry(rawExecutor, data);
                    Map<String, Object> output = executor.execute(data, ctx);
                    long duration = System.currentTimeMillis() - nodeStart;

                    // Build node log
                    Map<String, Object> log = new HashMap<>();
                    log.put("nodeId", nodeId);
                    log.put("nodeType", type);
                    log.put("status", "SUCCESS");
                    log.put("output", output);
                    log.put("durationMs", duration);
                    if (phaseIndex != null) log.put("phaseIndex", phaseIndex);
                    if (totalPhases != null) log.put("totalPhases", totalPhases);
                    nodeLogs.add(log);

                    // Notify: node SUCCESS
                    if (progressCallback != null) {
                        Map<String, Object> success = new HashMap<>();
                        success.put("nodeId", nodeId);
                        success.put("nodeType", type);
                        success.put("label", label);
                        success.put("message", phasePrefix + "Completed in " + duration + "ms");
                        success.put("durationMs", duration);
                        if (phaseIndex != null) success.put("phaseIndex", phaseIndex);
                        if (totalPhases != null) success.put("totalPhases", totalPhases);

                        // Attach output metadata for frontend display (branch, confidence, token usage)
                        if (output.containsKey("branch")) {
                            success.put("branch", output.get("branch"));
                        }
                        if (output.containsKey("confidence")) {
                            success.put("confidence", output.get("confidence"));
                        }
                        if (output.containsKey("reasoning")) {
                            success.put("reasoning", output.get("reasoning"));
                        }
                        if (output.containsKey("_tokenUsage")) {
                            success.put("tokenUsage", output.get("_tokenUsage"));
                        }

                        // human_review: judge models disagree — keep SUCCESS but signal frontend
                        if ("human_review".equals(output.get("branch"))) {
                            success.put("warning", "AI 评审出现分歧，请人工裁决");
                        }

                        progressCallback.accept(success);
                    }

                    // Merge output into state
                    Map<String, Object> updated = new HashMap<>(stateMap);
                    updated.put(nodeId, output);

                    // Track retry count for judge nodes: if verdict is "false", increment counter
                    if ("judge".equals(type) && "false".equals(output.get("branch"))) {
                        int retryCount = stateMap.containsKey(retryKey)
                                ? ((Number) stateMap.get(retryKey)).intValue() : 0;
                        updated.put(retryKey, retryCount + 1);
                    }

                    return updated;
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - nodeStart;

                    Map<String, Object> log = new HashMap<>();
                    log.put("nodeId", nodeId);
                    log.put("nodeType", type);
                    log.put("status", "FAILED");
                    log.put("error", e.getMessage());
                    log.put("durationMs", duration);
                    if (phaseIndex != null) log.put("phaseIndex", phaseIndex);
                    if (totalPhases != null) log.put("totalPhases", totalPhases);
                    nodeLogs.add(log);

                    // Notify: node FAILED
                    if (progressCallback != null) {
                        Map<String, Object> failed = new HashMap<>();
                        failed.put("nodeId", nodeId);
                        failed.put("nodeType", type);
                        failed.put("label", label);
                        failed.put("status", "FAILED");
                        failed.put("message", phasePrefix + "Failed: " + e.getMessage());
                        failed.put("durationMs", duration);
                        if (phaseIndex != null) failed.put("phaseIndex", phaseIndex);
                        if (totalPhases != null) failed.put("totalPhases", totalPhases);
                        progressCallback.accept(failed);
                    }

                    throw new RuntimeException("Node execution failed: " + nodeId + " - " + e.getMessage(), e);
                }
            }));
        }

        // Collect condition/judge node IDs for special edge handling
        Set<String> conditionNodeIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            String nodeType = (String) node.get("type");
            if ("condition".equals(nodeType) || "judge".equals(nodeType)) {
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

                // Defensive: skip edges referencing nodes that don't exist (stale from previous decompositions)
                if (!nodeMap.containsKey(source)) {
                    log.warn("Skipping edge {} → {}: source node '{}' not found in workflow", source, target, source);
                    continue;
                }
                if (!nodeMap.containsKey(target)) {
                    log.warn("Skipping edge {} → {}: target node '{}' not found in workflow", source, target, target);
                    continue;
                }

                if (conditionNodeIds.contains(source)) {
                    // Read branch from 'branch' field first, fallback to 'sourceHandle' (used by frontend)
                    String branch = (String) edge.getOrDefault("branch", edge.getOrDefault("sourceHandle", "true"));
                    conditionPathMaps.computeIfAbsent(source, k -> new LinkedHashMap<>()).put(branch, target);
                } else {
                    graph.addEdge(source, target);
                }
            }

            // Register conditional edges for each condition node
            for (Map.Entry<String, Map<String, String>> entry : conditionPathMaps.entrySet()) {
                String condNodeId = entry.getKey();
                Map<String, String> pathMap = entry.getValue();
                // Ensure all branches have fallback targets
                if (!pathMap.containsKey("true")) {
                    pathMap.put("true", StateGraph.END);
                }
                if (!pathMap.containsKey("false")) {
                    pathMap.put("false", StateGraph.END);
                }
                // human_review: judge models disagree — default to "true" path (pass through)
                if (!pathMap.containsKey("human_review")) {
                    pathMap.put("human_review", pathMap.get("true"));
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

        // Compile and invoke with increased recursion limit for multi-phase workflows
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("_userInput", userInput);

        var compiled = graph.compile(
            CompileConfig.builder()
                .recursionLimit(200)
                .build());
        var optState = compiled.invoke(initialState);
        AgentState resultState = optState.orElse(new AgentState(initialState));

        // Convert result with collected nodeLogs
        return convertToResult(resultState, nodeLogs);
    }

    private NodeExecutor wrapWithRetry(NodeExecutor executor, Map<String, Object> nodeData) {
        RetryConfig retryConfig = RetryableExecutor.fromNodeData(nodeData);
        if (retryConfig != null) {
            return new RetryableExecutor(executor, retryConfig);
        }
        return executor;
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
