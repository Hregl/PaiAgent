package com.paiagent.engine;

import java.util.Map;

/**
 * Interface for all node executors.
 */
public interface NodeExecutor {
    /**
     * Execute a node with given configuration and context.
     * @param nodeData the node's data/configuration from the workflow definition
     * @param context the execution context with results from previous nodes
     * @return output key-value pairs to store in context
     */
    Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) throws Exception;
}
