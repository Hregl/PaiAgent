package com.paiagent.engine;

import java.util.Map;

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
}
