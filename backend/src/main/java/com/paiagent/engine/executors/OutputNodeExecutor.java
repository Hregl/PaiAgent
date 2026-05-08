package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OutputNodeExecutor implements NodeExecutor {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) {
        Map<String, Object> result = new HashMap<>();

        // Process output references
        List<Map<String, String>> outputs = (List<Map<String, String>>) nodeData.get("outputs");
        if (outputs != null) {
            for (Map<String, String> output : outputs) {
                String key = output.get("key");
                String ref = output.get("ref");
                if (ref != null && ref.contains(".")) {
                    String[] parts = ref.split("\\.", 2);
                    Object value = context.getNodeOutput(parts[0], parts[1]);
                    result.put(key, value);
                }
            }
        }

        // Process response template
        String template = (String) nodeData.getOrDefault("responseTemplate", "{{output}}");
        String response = context.resolveTemplate(template);
        result.put("text", response);

        // Pass through audio URL if available
        if (result.containsKey("output") && result.get("output") instanceof String) {
            String outputVal = (String) result.get("output");
            if (outputVal.endsWith(".mp3") || outputVal.endsWith(".wav") || outputVal.startsWith("/files/")) {
                result.put("audioUrl", outputVal);
            }
        }

        return result;
    }
}
