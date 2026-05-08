package com.paiagent.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Execution context that holds intermediate results between nodes.
 * Maps nodeId -> (outputKey -> value)
 */
public class ExecutionContext {
    private final Map<String, Map<String, Object>> nodeOutputs = new HashMap<>();

    public void setNodeOutput(String nodeId, String key, Object value) {
        nodeOutputs.computeIfAbsent(nodeId, k -> new HashMap<>()).put(key, value);
    }

    public void setNodeOutputs(String nodeId, Map<String, Object> outputs) {
        nodeOutputs.put(nodeId, outputs);
    }

    public Object getNodeOutput(String nodeId, String key) {
        Map<String, Object> outputs = nodeOutputs.get(nodeId);
        return outputs != null ? outputs.get(key) : null;
    }

    public Map<String, Object> getNodeOutputs(String nodeId) {
        return nodeOutputs.getOrDefault(nodeId, new HashMap<>());
    }

    public Map<String, Map<String, Object>> getAllOutputs() {
        return nodeOutputs;
    }

    /**
     * Resolve a template string like "{{node_1.output}}" by replacing
     * references with actual values from the context.
     */
    public String resolveTemplate(String template) {
        if (template == null) return "";
        String result = template;
        // Match {{nodeId.key}} pattern
        while (result.contains("{{") && result.contains("}}")) {
            int start = result.indexOf("{{");
            int end = result.indexOf("}}", start);
            if (end == -1) break;

            String ref = result.substring(start + 2, end).trim();
            String[] parts = ref.split("\\.", 2);
            String value = "";
            if (parts.length == 2) {
                Object resolved = getNodeOutput(parts[0], parts[1]);
                value = resolved != null ? resolved.toString() : "";
            } else if (parts.length == 1) {
                // Try to find in any node
                Object resolved = getNodeOutput(parts[0], "output");
                value = resolved != null ? resolved.toString() : "";
            }
            result = result.substring(0, start) + value + result.substring(end + 2);
        }
        return result;
    }
}
