package com.paiagent.engine.executors;

import com.paiagent.adapter.SpringAiChatService;
import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class LLMNodeExecutor implements NodeExecutor {

    private final SpringAiChatService chatService;

    public LLMNodeExecutor(SpringAiChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) throws Exception {
        String provider = (String) nodeData.getOrDefault("provider", "deepseek");
        String model = (String) nodeData.getOrDefault("model", "");
        String promptTemplate = (String) nodeData.getOrDefault("prompt", "");
        Double temperature = nodeData.get("temperature") != null ?
                ((Number) nodeData.get("temperature")).doubleValue() : 0.7;
        Integer maxTokens = nodeData.get("maxTokens") != null ?
                ((Number) nodeData.get("maxTokens")).intValue() : 2048;

        // Resolve template variables in prompt
        String resolvedPrompt = context.resolveTemplate(promptTemplate);

        // Call LLM via Spring AI
        Map<String, Object> config = new HashMap<>();
        config.put("model", model);
        config.put("temperature", temperature);
        config.put("maxTokens", maxTokens);

        String response = chatService.chat(provider, resolvedPrompt, config);

        Map<String, Object> output = new HashMap<>();
        output.put("output", response);
        return output;
    }
}
