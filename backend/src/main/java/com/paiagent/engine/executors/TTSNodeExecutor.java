package com.paiagent.engine.executors;

import com.paiagent.adapter.TTSAdapter;
import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TTSNodeExecutor implements NodeExecutor {

    private final TTSAdapter ttsAdapter;

    public TTSNodeExecutor(TTSAdapter ttsAdapter) {
        this.ttsAdapter = ttsAdapter;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) throws Exception {
        String inputRef = (String) nodeData.getOrDefault("inputRef", "");
        String voiceId = (String) nodeData.getOrDefault("voiceId", "zhiyan");

        // Resolve input text from reference
        String inputText;
        if (inputRef.contains(".")) {
            String[] parts = inputRef.split("\\.", 2);
            Object value = context.getNodeOutput(parts[0], parts[1]);
            inputText = value != null ? value.toString() : "";
        } else {
            inputText = context.resolveTemplate("{{" + inputRef + "}}");
        }

        if (inputText.isEmpty()) {
            throw new IllegalArgumentException("TTS input text is empty");
        }

        // Call TTS adapter
        String audioUrl = ttsAdapter.synthesize(inputText, voiceId);

        Map<String, Object> output = new HashMap<>();
        output.put("audioUrl", audioUrl);
        output.put("inputText", inputText);
        return output;
    }
}
