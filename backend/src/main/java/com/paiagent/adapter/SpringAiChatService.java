package com.paiagent.adapter;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified LLM service powered by Spring AI.
 * Supports DeepSeek, Qwen, ChatGLM, and AI Ping via OpenAI-compatible API.
 */
@Service
public class SpringAiChatService {

    private final Map<String, ChatClient> clients = new HashMap<>();

    @Value("${llm.deepseek.api-key:}")
    private String deepseekApiKey;
    @Value("${llm.deepseek.base-url:https://api.deepseek.com/v1}")
    private String deepseekBaseUrl;

    @Value("${llm.qwen.api-key:}")
    private String qwenApiKey;
    @Value("${llm.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String qwenBaseUrl;

    @Value("${llm.chatglm.api-key:}")
    private String chatglmApiKey;
    @Value("${llm.chatglm.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String chatglmBaseUrl;

    @Value("${llm.aiping.api-key:}")
    private String aipingApiKey;
    @Value("${llm.aiping.base-url:}")
    private String aipingBaseUrl;

    @PostConstruct
    public void init() {
        registerProvider("deepseek", deepseekApiKey, deepseekBaseUrl);
        registerProvider("qwen", qwenApiKey, qwenBaseUrl);
        registerProvider("chatglm", chatglmApiKey, chatglmBaseUrl);
        registerProvider("aiping", aipingApiKey, aipingBaseUrl);
    }

    private void registerProvider(String name, String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }
        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);
        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi);
        clients.put(name, ChatClient.builder(chatModel).build());
    }

    /**
     * Send a chat message to the specified LLM provider.
     * Supports per-node API key and base URL overrides.
     *
     * @param provider     the LLM provider (deepseek, qwen, chatglm, aiping)
     * @param prompt       the user prompt
     * @param config       configuration including model, temperature, maxTokens
     * @param nodeApiKey   optional per-node API key override
     * @param nodeBaseUrl  optional per-node base URL override
     * @return the LLM response text
     */
    public String chat(String provider, String prompt, Map<String, Object> config,
                       String nodeApiKey, String nodeBaseUrl) {
        ChatClient client;

        if (nodeApiKey != null && !nodeApiKey.isBlank()) {
            // Use node-level credentials — create a one-off ChatClient
            String baseUrl = (nodeBaseUrl != null && !nodeBaseUrl.isBlank())
                    ? stripTrailingSlash(nodeBaseUrl)
                    : getDefaultBaseUrl(provider);
            OpenAiApi openAiApi = new OpenAiApi(baseUrl, nodeApiKey);
            OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi);
            client = ChatClient.builder(chatModel).build();
        } else {
            // Fall back to globally configured provider
            client = clients.get(provider);
        }

        if (client == null) {
            throw new IllegalArgumentException("LLM provider not configured or unknown: " + provider);
        }

        String model = (String) config.getOrDefault("model", "");
        double temperature = ((Number) config.getOrDefault("temperature", 0.7)).doubleValue();
        int maxTokens = ((Number) config.getOrDefault("maxTokens", 2048)).intValue();

        UserMessage userMessage = new UserMessage(prompt);
        Prompt chatPrompt = new Prompt(userMessage, OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build());

        try {
            return client.prompt(chatPrompt).call().content();
        } catch (RuntimeException e) {
            throw new RuntimeException(
                "LLM call failed [provider=" + provider + ", model=" + model + "]: " + e.getMessage(), e);
        }
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String getDefaultBaseUrl(String provider) {
        return switch (provider) {
            case "deepseek" -> "https://api.deepseek.com/v1";
            case "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "chatglm" -> "https://open.bigmodel.cn/api/paas/v4";
            default -> "";
        };
    }

    /**
     * Check if a provider is configured and available.
     */
    public boolean hasProvider(String provider) {
        return clients.containsKey(provider);
    }
}
