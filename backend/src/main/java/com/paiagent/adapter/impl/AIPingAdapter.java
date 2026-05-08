package com.paiagent.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paiagent.adapter.LLMAdapter;
import okhttp3.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI Ping adapter - OpenAI compatible API format.
 */
public class AIPingAdapter implements LLMAdapter {

    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AIPingAdapter(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String chat(String prompt, Map<String, Object> config) throws Exception {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new RuntimeException("AI Ping base URL not configured");
        }

        String model = (String) config.getOrDefault("model", "");
        double temperature = ((Number) config.getOrDefault("temperature", 0.7)).doubleValue();
        int maxTokens = ((Number) config.getOrDefault("maxTokens", 2048)).intValue();

        String requestBody = mapper.writeValueAsString(Map.of(
                "model", model,
                "messages", new Object[]{Map.of("role", "user", "content", prompt)},
                "temperature", temperature,
                "max_tokens", maxTokens
        ));

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new RuntimeException("AI Ping API error: " + response.code() + " - " + errorBody);
            }
            String body = response.body().string();
            JsonNode json = mapper.readTree(body);
            return json.at("/choices/0/message/content").asText();
        }
    }
}
