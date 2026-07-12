package com.paiagent.engine.executors;

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
import java.util.Map;

/**
 * AI-powered judgment node. Calls an LLM to evaluate whether a worker's
 * output satisfies given completion criteria, returning a branch decision
 * ("true" or "false") with reasoning and confidence score.
 *
 * When confidence is low (< 0.7), a second LLM with a different provider/model
 * is consulted. If the two judgments disagree, the node returns
 * branch="human_review" to flag the decision for manual intervention.
 *
 * Only available in LangGraph engine.
 */
@Component
public class JudgeNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(JudgeNodeExecutor.class);

    /**
     * Threshold below which a second opinion is requested.
     */
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.7;

    private final SpringAiChatService chatService;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;

    public JudgeNodeExecutor(SpringAiChatService chatService, ObjectMapper objectMapper,
                             PromptLoader promptLoader) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.promptLoader = promptLoader;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) throws Exception {
        String leftRef = (String) nodeData.get("leftRef");
        String criteria = (String) nodeData.getOrDefault("criteria", "");
        String provider = (String) nodeData.getOrDefault("provider", "deepseek");
        String model = (String) nodeData.getOrDefault("model", "");
        String apiKey = (String) nodeData.get("apiKey");
        String apiBaseUrl = (String) nodeData.get("apiBaseUrl");
        Double temperature = nodeData.get("temperature") != null
                ? ((Number) nodeData.get("temperature")).doubleValue() : 0.1;
        Integer maxTokens = nodeData.get("maxTokens") != null
                ? ((Number) nodeData.get("maxTokens")).intValue() : 256;

        // Resolve the worker output from context
        String workerOutput = resolveLeftRef(leftRef, context);
        log.info("Judge evaluating: criteria='{}', workerOutput length={}", criteria, workerOutput.length());

        // Primary judgment
        String prompt = buildJudgePrompt(workerOutput, criteria);
        Map<String, Object> config = new HashMap<>();
        config.put("model", model);
        config.put("temperature", temperature);
        config.put("maxTokens", maxTokens);

        var chatResult = chatService.chatWithUsage(provider, prompt, config,
                (apiKey != null && !apiKey.isBlank()) ? apiKey : null,
                (apiBaseUrl != null && !apiBaseUrl.isBlank()) ? apiBaseUrl : null);

        JudgeResult primary = parseJudgment(chatResult.content());

        // Confidence-based second review
        if (primary.confidence < LOW_CONFIDENCE_THRESHOLD) {
            log.info("Judge confidence low ({}) for node, requesting second opinion", primary.confidence);
            JudgeResult secondOpinion = getSecondOpinion(workerOutput, criteria, provider, apiKey, apiBaseUrl);

            if (secondOpinion != null && secondOpinion.verdict() != primary.verdict()) {
                log.warn("Judge verdicts disagree: primary={}, second={}",
                    primary.verdict(), secondOpinion.verdict());
                Map<String, Object> output = new HashMap<>();
                output.put("branch", "human_review");
                output.put("reasoning", String.format(
                    "判定结果存在分歧 — 主评审(%s): %s (置信度%.0f%%), 复核(%s/%s): %s (置信度%.0f%%)",
                    provider, primary.reason(), primary.confidence * 100,
                    secondOpinion.provider(), secondOpinion.model(),
                    secondOpinion.verdict(), secondOpinion.confidence * 100));
                output.put("passed", false);
                output.put("confidence", Math.min(primary.confidence, secondOpinion.confidence));
                return output;
            }
        }

        boolean passed = primary.verdict();
        log.info("Judge result: passed={}, confidence={}, reason={}",
            passed, primary.confidence, primary.reason());

        Map<String, Object> output = new HashMap<>();
        output.put("branch", passed ? "true" : "false");
        output.put("reasoning", primary.reason());
        output.put("passed", passed);
        output.put("confidence", primary.confidence);
        return output;
    }

    /**
     * Get a second opinion using a different model/provider when the primary
     * judgment has low confidence.
     */
    private JudgeResult getSecondOpinion(String workerOutput, String criteria,
                                          String primaryProvider, String apiKey, String apiBaseUrl) {
        // Pick a fallback provider different from the primary
        String fallbackProvider = selectFallbackProvider(primaryProvider);
        String fallbackModel = getFallbackModel(fallbackProvider);

        if (!chatService.hasProvider(fallbackProvider)) {
            log.warn("Fallback provider {} not configured, skipping second opinion", fallbackProvider);
            return null;
        }

        String prompt = buildJudgePrompt(workerOutput, criteria);
        Map<String, Object> config = new HashMap<>();
        config.put("model", fallbackModel);
        config.put("temperature", 0.0); // colder for second opinion
        config.put("maxTokens", 256);

        try {
            var secondResult = chatService.chatWithUsage(fallbackProvider, prompt, config, apiKey, apiBaseUrl);
            JudgeResult result = parseJudgment(secondResult.content());
            log.info("Second opinion: provider={} model={} verdict={} confidence={}",
                fallbackProvider, fallbackModel, result.verdict(), result.confidence);
            return result;
        } catch (Exception e) {
            log.warn("Second opinion call failed: {}", e.getMessage());
            return null;
        }
    }

    private String selectFallbackProvider(String primary) {
        // Rotate through available providers, preferring one different from primary
        String[] candidates = {"deepseek", "qwen", "chatglm"};
        for (String c : candidates) {
            if (!c.equals(primary) && chatService.hasProvider(c)) {
                return c;
            }
        }
        // If no alternative is available, return primary anyway (caller handles null check)
        return candidates[0].equals(primary) ? candidates[1] : candidates[0];
    }

    private String getFallbackModel(String provider) {
        return switch (provider) {
            case "deepseek" -> "deepseek-chat";
            case "qwen" -> "qwen-turbo";
            case "chatglm" -> "glm-4-flash";
            default -> "";
        };
    }

    /**
     * Resolve a leftRef like "worker_1.output" from the execution context.
     */
    private String resolveLeftRef(String leftRef, ExecutionContext context) {
        if (leftRef == null || leftRef.isBlank()) {
            return "";
        }
        String clean = leftRef.trim();
        if (clean.startsWith("{{") && clean.endsWith("}}")) {
            clean = clean.substring(2, clean.length() - 2).trim();
        }
        if (clean.contains(".")) {
            String[] parts = clean.split("\\.", 2);
            Object resolved = context.getNodeOutput(parts[0], parts[1]);
            return resolved != null ? resolved.toString() : "";
        }
        return context.resolveTemplate(leftRef);
    }

    /**
     * Build a judgment prompt from the externalized template.
     */
    private String buildJudgePrompt(String workerOutput, String criteria) {
        return promptLoader.render("judge-system", Map.of(
            "criteria", criteria,
            "workerOutput", workerOutput
        ));
    }

    /**
     * Parse the LLM response to extract the structured judgment.
     * Expects JSON: {"verdict": true/false, "reason": "...", "confidence": 0.85, "missing": [...]}
     * Falls back to string matching if JSON parsing fails.
     */
    private JudgeResult parseJudgment(String response) {
        if (response == null || response.isBlank()) {
            log.warn("Judge received empty response, defaulting to false");
            return new JudgeResult(false, "Empty response from judge LLM", 0.0, "", "");
        }
        try {
            String json = JsonExtractor.extract(response);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

            boolean verdict;
            Object verdictObj = parsed.get("verdict");
            if (verdictObj instanceof Boolean b) {
                verdict = b;
            } else {
                verdict = verdictObj != null && "true".equalsIgnoreCase(verdictObj.toString());
            }

            String reason = parsed.getOrDefault("reason", "").toString();

            double confidence = 0.5; // default mid-confidence
            Object confObj = parsed.get("confidence");
            if (confObj instanceof Number n) {
                confidence = n.doubleValue();
            }

            return new JudgeResult(verdict, reason, confidence, "", "");
        } catch (Exception e) {
            log.warn("Failed to parse judge JSON response, falling back to string match: {}", e.getMessage());
        }
        // Fallback: legacy string matching
        String firstLine = response.lines().findFirst().orElse("").trim().toLowerCase();
        boolean passed = firstLine.startsWith("true") || firstLine.startsWith("pass")
                || firstLine.contains("通过") || firstLine.contains("true") || firstLine.contains("pass");
        return new JudgeResult(passed, "Fallback string match", passed ? 0.4 : 0.4, "", "");
    }

    /**
     * Immutable result of a judgment call.
     */
    private record JudgeResult(boolean verdict, String reason, double confidence,
                               String provider, String model) {
        JudgeResult(boolean verdict, String reason, double confidence, String provider, String model) {
            this.verdict = verdict;
            this.reason = reason != null ? reason : "";
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            this.provider = provider;
            this.model = model;
        }
    }
}
