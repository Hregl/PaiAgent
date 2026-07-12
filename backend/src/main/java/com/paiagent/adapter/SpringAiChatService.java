package com.paiagent.adapter;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import reactor.core.publisher.Flux;

/**
 * Unified LLM service powered by Spring AI.
 * Supports DeepSeek, Qwen, ChatGLM, and AI Ping via OpenAI-compatible API.
 */
@Service
public class SpringAiChatService {

    private final Map<String, ChatClient> clients = new HashMap<>();

    @Value("${llm.deepseek.api-key:}")
    private String deepseekApiKey;
    @Value("${llm.deepseek.base-url:https://api.deepseek.com}")
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
     *
     * @return the LLM response text (for backward compatibility)
     */
    public String chat(String provider, String prompt, Map<String, Object> config,
                       String nodeApiKey, String nodeBaseUrl) {
        return chatWithUsage(provider, prompt, config, nodeApiKey, nodeBaseUrl).content();
    }

    /**
     * Send a chat message and return both content and token usage.
     */
    public ChatResult chatWithUsage(String provider, String prompt, Map<String, Object> config,
                                     String nodeApiKey, String nodeBaseUrl) {
        var clientAndUrl = resolveClient(provider, nodeApiKey, nodeBaseUrl);
        ChatClient client = clientAndUrl.client;
        String effectiveBaseUrl = clientAndUrl.baseUrl;

        if (client == null) {
            throw new IllegalArgumentException("LLM provider not configured or unknown: " + provider);
        }

        String model = (String) config.getOrDefault("model", "");
        double temperature = ((Number) config.getOrDefault("temperature", 0.7)).doubleValue();
        Object maxTokensObj = config.get("maxTokens");

        var optionsBuilder = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature);
        if (maxTokensObj instanceof Number n && n.intValue() > 0) {
            optionsBuilder.maxTokens(n.intValue());
        }
        // No maxTokens = let LLM use its own default ceiling

        UserMessage userMessage = new UserMessage(prompt);
        Prompt chatPrompt = new Prompt(userMessage, optionsBuilder.build());

        try {
            ChatResponse response = client.prompt(chatPrompt).call().chatResponse();
            String content = response.getResult().getOutput().getText();
            var usage = response.getMetadata().getUsage();
            Long pTokens = usage != null ? usage.getPromptTokens() : null;
            Long cTokens = usage != null ? usage.getGenerationTokens() : null;
            Long tTokens = usage != null ? usage.getTotalTokens() : null;
            int promptTokens = pTokens != null ? pTokens.intValue() : 0;
            int completionTokens = cTokens != null ? cTokens.intValue() : 0;
            int totalTokens = tTokens != null ? tTokens.intValue() : 0;
            return new ChatResult(content, promptTokens, completionTokens, totalTokens);
        } catch (RuntimeException e) {
            throw new RuntimeException(
                "LLM call failed [provider=" + provider + ", model=" + model
                + ", baseUrl=" + effectiveBaseUrl + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Stream a chat response as a reactive Flux. Each emission is a text chunk.
     * Useful for real-time display of LLM output in the frontend.
     */
    public Flux<String> chatStream(String provider, String prompt, Map<String, Object> config,
                                    String nodeApiKey, String nodeBaseUrl) {
        var clientAndUrl = resolveClient(provider, nodeApiKey, nodeBaseUrl);
        ChatClient client = clientAndUrl.client;

        if (client == null) {
            return Flux.error(new IllegalArgumentException(
                "LLM provider not configured or unknown: " + provider));
        }

        String model = (String) config.getOrDefault("model", "");
        double temperature = ((Number) config.getOrDefault("temperature", 0.7)).doubleValue();
        Object maxTokensObj = config.get("maxTokens");

        var optionsBuilder = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature);
        if (maxTokensObj instanceof Number n && n.intValue() > 0) {
            optionsBuilder.maxTokens(n.intValue());
        }
        // No maxTokens = let LLM use its own default ceiling

        UserMessage userMessage = new UserMessage(prompt);
        Prompt chatPrompt = new Prompt(userMessage, optionsBuilder.build());

        return client.prompt(chatPrompt).stream().content();
    }

    /**
     * Check if a provider is configured and available.
     */
    public boolean hasProvider(String provider) {
        return clients.containsKey(provider);
    }

    private ClientAndUrl resolveClient(String provider, String nodeApiKey, String nodeBaseUrl) {
        if (nodeApiKey != null && !nodeApiKey.isBlank()) {
            String baseUrl = (nodeBaseUrl != null && !nodeBaseUrl.isBlank())
                    ? stripTrailingSlash(nodeBaseUrl)
                    : getDefaultBaseUrl(provider);
            OpenAiApi openAiApi = new OpenAiApi(baseUrl, nodeApiKey);
            OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi);
            return new ClientAndUrl(ChatClient.builder(chatModel).build(), baseUrl);
        }
        return new ClientAndUrl(clients.get(provider), "global");
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String getDefaultBaseUrl(String provider) {
        return switch (provider) {
            case "deepseek" -> "https://api.deepseek.com";
            case "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "chatglm" -> "https://open.bigmodel.cn/api/paas/v4";
            default -> "";
        };
    }

    private record ClientAndUrl(ChatClient client, String baseUrl) {}

    /**
     * Result of a chat call including token usage for cost tracking.
     */
    public record ChatResult(String content, int promptTokens, int completionTokens, int totalTokens) {}
}
