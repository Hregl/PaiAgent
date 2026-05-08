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
                if (ref != null) {
                    // Strip {{ }} wrapping if present (defensive)
                    String cleanRef = stripTemplateBraces(ref);
                    if (cleanRef.contains(".")) {
                        String[] parts = cleanRef.split("\\.", 2);
                        Object value = context.getNodeOutput(parts[0], parts[1]);
                        result.put(key, value);
                    }
                }
            }
        }

        // Process response template — resolve {{key}} against local result map
        String template = (String) nodeData.getOrDefault("responseTemplate", "{{output}}");
        String response = resolveFromResultMap(template, result);
        if (response.isEmpty() || response.equals(template)) {
            // Fallback: try context-level template resolution
            response = context.resolveTemplate(template);
        }
        result.put("text", response);

        return result;
    }

    /**
     * Strip {{ and }} wrapping from a reference string.
     */
    private String stripTemplateBraces(String ref) {
        String clean = ref.trim();
        if (clean.startsWith("{{") && clean.endsWith("}}")) {
            clean = clean.substring(2, clean.length() - 2).trim();
        }
        return clean;
    }

    /**
     * Replace {{key}} placeholders with values from the result map.
     */
    private String resolveFromResultMap(String template, Map<String, Object> values) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
