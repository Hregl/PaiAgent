package com.paiagent.engine.executors;

import com.paiagent.adapter.SpringAiChatService;
import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI-powered judgment node. Calls an LLM to evaluate whether a worker's
 * output satisfies given completion criteria, returning a branch decision
 * ("true" or "false") with reasoning.
 *
 * Unlike ConditionNodeExecutor (which does simple string matching),
 * this node uses AI to perform semantic evaluation.
 *
 * Only available in LangGraph engine — the DAG engine cannot route
 * conditional branches.
 */
@Component
public class JudgeNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(JudgeNodeExecutor.class);
    private final SpringAiChatService chatService;

    public JudgeNodeExecutor(SpringAiChatService chatService) {
        this.chatService = chatService;
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

        // Build judgment prompt
        String prompt = buildJudgePrompt(workerOutput, criteria);

        // Call LLM
        Map<String, Object> config = new HashMap<>();
        config.put("model", model);
        config.put("temperature", temperature);
        config.put("maxTokens", maxTokens);

        String response = chatService.chat(provider, prompt, config,
                (apiKey != null && !apiKey.isBlank()) ? apiKey : null,
                (apiBaseUrl != null && !apiBaseUrl.isBlank()) ? apiBaseUrl : null);

        // Parse judgment result
        boolean passed = parseJudgment(response);
        log.info("Judge result: passed={}, response preview={}", passed,
                response.length() > 80 ? response.substring(0, 80) + "..." : response);

        Map<String, Object> output = new HashMap<>();
        output.put("branch", passed ? "true" : "false");
        output.put("reasoning", response);
        output.put("passed", passed);
        return output;
    }

    /**
     * Resolve a leftRef like "worker_1.output" from the execution context.
     */
    private String resolveLeftRef(String leftRef, ExecutionContext context) {
        if (leftRef == null || leftRef.isBlank()) {
            return "";
        }
        String clean = leftRef.trim();
        // Strip {{ }} if present
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
     * Build a prompt instructing the LLM to judge whether the worker output
     * satisfies the criteria. Returns ONLY "true" or "false" with a brief reason.
     */
    private String buildJudgePrompt(String workerOutput, String criteria) {
        return """
            You are a strict quality judge. Your task is to evaluate whether
            the following work output satisfies the given completion criteria.

            COMPLETION CRITERIA:
            %s

            WORK OUTPUT TO EVALUATE:
            %s

            INSTRUCTIONS:
            1. Carefully compare the work output against each criterion
            2. If the output satisfies ALL criteria, respond: true
            3. If ANY criterion is not met, respond: false
            4. After your true/false verdict, give a one-line reason in Chinese

            Respond in this exact format (no markdown, no extra text):
            true/false
            原因: <one-line reason>
            """.formatted(criteria, workerOutput);
    }

    /**
     * Parse the LLM response to extract the boolean judgment.
     * Expects format: "true\\n原因: ..." or "false\\n原因: ..."
     */
    private boolean parseJudgment(String response) {
        if (response == null || response.isBlank()) {
            log.warn("Judge received empty response, defaulting to false");
            return false;
        }
        String firstLine = response.lines().findFirst().orElse("").trim().toLowerCase();
        // Accept various positive signals
        if (firstLine.startsWith("true") || firstLine.startsWith("pass")
                || firstLine.contains("通过")) {
            return true;
        }
        return false;
    }
}
