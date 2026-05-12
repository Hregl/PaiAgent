package com.paiagent.engine;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Workflow engine interface. Supports both DAG and LangGraph implementations.
 */
public interface WorkflowEngine {
    /**
     * Execute a workflow definition with user input.
     * @param definitionJson JSON string containing nodes and edges
     * @param userInput the user's input text
     * @return execution result with nodeLogs and output
     */
    Map<String, Object> execute(String definitionJson, String userInput) throws Exception;

    /**
     * Execute a workflow with per-node progress callback for SSE streaming.
     * Default implementation falls back to {@link #execute} without progress.
     * Override in engines that support real-time progress reporting.
     *
     * @param definitionJson JSON string containing nodes and edges
     * @param userInput the user's input text
     * @param progressCallback receives progress map with keys:
     *        nodeId, nodeType, label, status (RUNNING/SUCCESS/FAILED), message, durationMs
     * @return execution result with nodeLogs and output
     */
    default Map<String, Object> executeWithProgress(String definitionJson, String userInput,
                                                     Consumer<Map<String, Object>> progressCallback) throws Exception {
        return execute(definitionJson, userInput);
    }
}
