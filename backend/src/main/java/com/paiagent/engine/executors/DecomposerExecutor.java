package com.paiagent.engine.executors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paiagent.adapter.SpringAiChatService;
import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public DecomposerExecutor(SpringAiChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute decomposer node during workflow runtime.
     * Reads taskDescription from nodeData, calls LLM to decompose,
     * and stores phases + summary in context for downstream nodes.
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
     *
     * @param taskDescription description of the overall task
     * @param provider        LLM provider (deepseek, qwen, etc.)
     * @param model           model name
     * @param apiKey          per-node API key (null to use global config)
     * @param apiBaseUrl      per-node API base URL (null to use global config)
     * @return DecompositionResult containing list of phases or error
     */
    public DecompositionResult decompose(String taskDescription, String provider, String model,
                                          String apiKey, String apiBaseUrl) {
        String prompt = buildDecomposePrompt(taskDescription);
        Map<String, Object> config = new HashMap<>();
        config.put("model", model != null && !model.isEmpty() ? model : "deepseek-chat");
        config.put("temperature", 0.3);
        config.put("maxTokens", 2048);

        log.info("Decomposing task with provider={} model={}, task length={}, nodeKey={}",
            provider, model, taskDescription.length(), apiKey != null ? "***" : "none");
        try {
            String response = chatService.chat(provider, prompt, config, apiKey, apiBaseUrl);
            log.info("Decompose response length={}", response.length());
            return parseResponse(response);
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
        return """
            You are a task decomposition expert. Your job is to break down a complex task into
            sequential phases (steps). Each phase must be a self-contained unit of work that can
            be verified independently.

            For each phase, provide:
            1. A short name
            2. A detailed description of what the worker AI should do
            3. Clear completion criteria that a judge AI can use to verify the phase is done

            IMPORTANT: Return ONLY valid JSON in the following format, no other text:
            {
              "phases": [
                {
                  "name": "Phase short name",
                  "description": "Detailed instructions for the worker AI to execute this phase...",
                  "criteria": "Specific criteria the judge AI should check to confirm completion..."
                }
              ]
            }

            Rules:
            - Phases must be sequential (phase 2 depends on phase 1, etc.)
            - Each phase description should be detailed enough for an AI to execute it
            - Each criteria should list specific, verifiable conditions
            - No more than 5 phases
            - Use the language of the task description

            Task to decompose:
            """ + taskDescription;
    }

    DecompositionResult parseResponse(String response) {
        // Try to extract JSON from the response (may be wrapped in markdown code blocks)
        String json = extractJson(response);
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

    private String extractJson(String response) {
        // Strip markdown code blocks if present
        Pattern pattern = Pattern.compile("```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // Find the outermost JSON object
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
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
