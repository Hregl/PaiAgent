package com.paiagent.engine.executors;

import com.paiagent.adapter.TTSAdapter;
import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TTSNodeExecutor implements NodeExecutor {

    private final TTSAdapter ttsAdapter;

    public TTSNodeExecutor(TTSAdapter ttsAdapter) {
        this.ttsAdapter = ttsAdapter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) throws Exception {
        String apiKey = (String) nodeData.getOrDefault("apiKey", "");
        String model = (String) nodeData.getOrDefault("model", "qwen3-tts-flash");
        List<Map<String, String>> inputs = (List<Map<String, String>>) nodeData.get("inputs");

        String resolvedText = "";
        String resolvedVoice = "Cherry";
        String resolvedLanguageType = "Auto";

        if (inputs != null) {
            for (Map<String, String> input : inputs) {
                String paramName = input.get("paramName");
                String paramType = input.getOrDefault("paramType", "reference");
                String value = input.getOrDefault("value", "");

                String resolvedValue;
                if ("input".equals(paramType)) {
                    resolvedValue = value;
                } else {
                    // Reference — resolve from context
                    String cleanRef = stripTemplateBraces(value);
                    if (cleanRef.contains(".")) {
                        String[] parts = cleanRef.split("\\.", 2);
                        Object refValue = context.getNodeOutput(parts[0], parts[1]);
                        resolvedValue = refValue != null ? refValue.toString() : "";
                    } else {
                        resolvedValue = cleanRef;
                    }
                }

                switch (paramName) {
                    case "text" -> resolvedText = resolvedValue;
                    case "voice" -> resolvedVoice = resolvedValue;
                    case "language_type" -> resolvedLanguageType = resolvedValue;
                }
            }
        }

        if (resolvedText.isEmpty()) {
            throw new IllegalArgumentException("TTS input text is empty");
        }

        // Build config map for TTS adapter
        Map<String, String> ttsConfig = new HashMap<>();
        ttsConfig.put("apiKey", apiKey);
        ttsConfig.put("model", model);
        ttsConfig.put("voice", resolvedVoice);
        ttsConfig.put("languageType", resolvedLanguageType);

        // Call TTS adapter
        String audioUrl = ttsAdapter.synthesize(resolvedText, ttsConfig);

        Map<String, Object> output = new HashMap<>();
        output.put("audioUrl", audioUrl);
        output.put("inputText", resolvedText);
        output.put("voice", resolvedVoice);
        return output;
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
}
