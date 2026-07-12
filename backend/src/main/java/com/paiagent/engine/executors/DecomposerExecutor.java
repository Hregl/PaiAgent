package com.paiagent.engine.executors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paiagent.adapter.SpringAiChatService;
import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;
import com.paiagent.util.JsonExtractor;
import com.paiagent.util.PromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls an LLM to decompose a task description into sequential phases.
 * Returns a structured list of Phase objects for frontend to generate nodes.
 * Also implements NodeExecutor so the decomposer node can run as part of
 * a workflow execution pipeline.
 */
@Component
public class DecomposerExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(DecomposerExecutor.class);
    private final SpringAiChatService chatService;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;

    public DecomposerExecutor(SpringAiChatService chatService, ObjectMapper objectMapper,
                              PromptLoader promptLoader) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.promptLoader = promptLoader;
    }

    /**
     * Execute decomposer node during workflow runtime.
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) throws Exception {
        String taskDescription = (String) nodeData.getOrDefault("taskDescription", "");
        String provider = (String) nodeData.getOrDefault("provider", "deepseek");
        String model = (String) nodeData.getOrDefault("model", "");
        String apiKey = (String) nodeData.getOrDefault("apiKey", "");
        String apiBaseUrl = (String) nodeData.getOrDefault("apiBaseUrl", "");

        if (taskDescription == null || taskDescription.isBlank()) {
            log.warn("Decomposer node has no taskDescription, returning empty phases");
            Map<String, Object> output = new HashMap<>();
            output.put("phases", List.of());
            output.put("phaseCount", 0);
            output.put("summary", "No task description provided");
            return output;
        }

        DecompositionResult result = decompose(taskDescription, provider, model,
            apiKey.isBlank() ? null : apiKey,
            apiBaseUrl.isBlank() ? null : apiBaseUrl);

        Map<String, Object> output = new HashMap<>();
        if (result.success && result.phases != null) {
            output.put("phases", result.phases);
            output.put("phaseCount", result.phases.size());
            output.put("summary", String.join(" → ",
                result.phases.stream().map(p -> p.name).toList()));
        } else {
            output.put("phases", List.of());
            output.put("phaseCount", 0);
            output.put("error", result.error != null ? result.error : "Decomposition failed");
        }
        return output;
    }

    /**
     * Decompose a task into phases using the specified LLM.
     */
    public DecompositionResult decompose(String taskDescription, String provider, String model,
                                          String apiKey, String apiBaseUrl) {
        String prompt = buildDecomposePrompt(taskDescription);
        Map<String, Object> config = new HashMap<>();
        config.put("model", model != null && !model.isEmpty() ? model : "deepseek-chat");
        config.put("temperature", 0.3);
        config.put("maxTokens", 2048);

        log.info("Decomposing task with provider={} model={}, task length={}",
            provider, model, taskDescription.length());
        try {
            var result = chatService.chatWithUsage(provider, prompt, config, apiKey, apiBaseUrl);
            log.info("Decompose response length={}, tokens={}", result.content().length(), result.totalTokens());
            return parseResponse(result.content());
        } catch (IllegalArgumentException e) {
            String envVar = getApiKeyEnvName(provider);
            String msg = "LLM provider '" + provider + "' 未配置。"
                + "请在节点配置中填写 API 密钥，或设置环境变量 " + envVar;
            log.warn("Decompose failed: {}", msg);
            return DecompositionResult.error(msg);
        } catch (RuntimeException e) {
            log.error("Decompose failed with unexpected error", e);
            return DecompositionResult.error("LLM 调用失败: " + e.getMessage());
        }
    }

    private String getApiKeyEnvName(String provider) {
        return switch (provider) {
            case "deepseek" -> "DEEPSEEK_API_KEY";
            case "qwen" -> "QWEN_API_KEY";
            case "chatglm" -> "CHATGLM_API_KEY";
            case "aiping" -> "AIPING_API_KEY";
            default -> provider.toUpperCase() + "_API_KEY";
        };
    }

    private String buildDecomposePrompt(String taskDescription) {
        return promptLoader.render("decompose-system", Map.of(
            "examples", "",
            "taskDescription", taskDescription
        )) + taskDescription;
    }

    DecompositionResult parseResponse(String response) {
        String json = JsonExtractor.extract(response);
        try {
            PhaseList phaseList = objectMapper.readValue(json, PhaseList.class);
            if (phaseList.phases == null || phaseList.phases.isEmpty()) {
                return DecompositionResult.error("AI returned empty phase list. Try rephrasing the task.");
            }
            log.info("Decomposed into {} phases: {}", phaseList.phases.size(),
                phaseList.phases.stream().map(p -> p.name).toList());
            return DecompositionResult.success(phaseList.phases);
        } catch (Exception e) {
            log.warn("Failed to parse decompose response: {}", e.getMessage());
            return DecompositionResult.error("Failed to parse AI response: " + e.getMessage());
        }
    }

    /**
     * Phase representation for JSON deserialization.
     */
    public static class Phase {
        @JsonProperty("name")
        public String name;

        @JsonProperty("description")
        public String description;

        @JsonProperty("criteria")
        public String criteria;

        public Phase() {}

        public Phase(String name, String description, String criteria) {
            this.name = name;
            this.description = description;
            this.criteria = criteria;
        }
    }

    private static class PhaseList {
        @JsonProperty("phases")
        public List<Phase> phases;
    }

    /**
     * Result wrapper for the decompose operation.
     */
    public static class DecompositionResult {
        public boolean success;
        public List<Phase> phases;
        public String error;

        public static DecompositionResult success(List<Phase> phases) {
            DecompositionResult r = new DecompositionResult();
            r.success = true;
            r.phases = phases;
            return r;
        }

        public static DecompositionResult error(String message) {
            DecompositionResult r = new DecompositionResult();
            r.success = false;
            r.error = message;
            return r;
        }
    }
}
