package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OutputNodeExecutor implements NodeExecutor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OutputNodeExecutor.class);

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) {
        Map<String, Object> result = new HashMap<>();

        // Process output references
        List<Map<String, String>> outputs = (List<Map<String, String>>) nodeData.get("outputs");
        log.info("Output node data keys: {}, outputs count: {}", nodeData.keySet(), outputs != null ? outputs.size() : 0);
        if (outputs != null) {
            for (Map<String, String> output : outputs) {
                // Support both new format (paramName/paramType/value) and legacy format (key/ref)
                String paramName = output.getOrDefault("paramName", output.get("key"));
                String paramType = output.getOrDefault("paramType", "reference");
                String value = output.getOrDefault("value", output.get("ref"));

                if (paramName == null || value == null) continue;

                if ("input".equals(paramType)) {
                    // Static value — use directly
                    result.put(paramName, value);
                } else {
                    // Reference — resolve from context
                    String cleanRef = stripTemplateBraces(value);
                    if (cleanRef.contains(".")) {
                        String[] parts = cleanRef.split("\\.", 2);
                        Object resolvedValue = context.getNodeOutput(parts[0], parts[1]);
                        log.info("Resolved reference {} -> {} = {}", cleanRef, paramName, resolvedValue);
                        result.put(paramName, resolvedValue);
                    } else {
                        // No dot in reference — treat as static value
                        result.put(paramName, cleanRef);
                    }
                }
            }
        }
        log.info("Output node result keys: {}", result.keySet());

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
