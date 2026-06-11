package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates a condition and returns which branch to follow ("true" or "false").
 * Only available in the LangGraph engine — the DAG engine rejects condition nodes.
 */
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConditionNodeExecutor.class);

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) {
        String leftRef = (String) nodeData.get("leftRef");
        String operator = (String) nodeData.getOrDefault("operator", "contains");
        String rightValue = (String) nodeData.getOrDefault("rightValue", "");

        log.info("Condition evaluation: leftRef={}, operator={}, rightValue={}", leftRef, operator, rightValue);

        // Resolve the left-hand reference from context
        String leftActual = resolveLeftRef(leftRef, context);
        log.info("Condition: resolved leftRef '{}' -> '{}'", leftRef, leftActual);

        boolean result = evaluate(leftActual, operator, rightValue);
        String branch = result ? "true" : "false";

        log.info("Condition result: {} -> branch={}", result, branch);

        Map<String, Object> output = new HashMap<>();
        output.put("branch", branch);
        return output;
    }

    /**
     * Resolve a leftRef like "llm_2.output" or "{{llm_2.output}}" from the context.
     */
    private String resolveLeftRef(String leftRef, ExecutionContext context) {
        if (leftRef == null || leftRef.isBlank()) {
            return "";
        }
        // Strip {{ }} if present
        String clean = leftRef.trim();
        if (clean.startsWith("{{") && clean.endsWith("}}")) {
            clean = clean.substring(2, clean.length() - 2).trim();
        }
        if (clean.contains(".")) {
            String[] parts = clean.split("\\.", 2);
            Object resolved = context.getNodeOutput(parts[0], parts[1]);
            return resolved != null ? resolved.toString() : "";
        }
        // Try as a single key in the last node
        return context.resolveTemplate(leftRef);
    }

    private boolean evaluate(String left, String operator, String right) {
        if (left == null) left = "";
        if (right == null) right = "";

        return switch (operator) {
            case "equals" -> left.equals(right);
            case "not_equals" -> !left.equals(right);
            case "contains" -> left.contains(right);
            case "starts_with" -> left.startsWith(right);
            case "is_empty" -> left.isEmpty();
            case "is_not_empty" -> !left.isEmpty();
            case "not_contains" -> !left.contains(right);
            case "greater_than" -> compareNumeric(left, right) > 0;
            case "less_than" -> compareNumeric(left, right) < 0;
            case "greater_or_equal" -> compareNumeric(left, right) >= 0;
            case "less_or_equal" -> compareNumeric(left, right) <= 0;
            case "matches_regex" -> {
                try {
                    yield Pattern.compile(right).matcher(left).find();
                } catch (PatternSyntaxException e) {
                    log.warn("matches_regex: invalid regex '{}': {}", right, e.getMessage());
                    yield false;
                }
            }
            default -> {
                log.warn("Unknown operator: {}, defaulting to false", operator);
                yield false;
            }
        };
    }

    private int compareNumeric(String a, String b) {
        try {
            double da = Double.parseDouble(a);
            double db = Double.parseDouble(b);
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            log.warn("Cannot compare as numbers: '{}' vs '{}'", a, b);
            return 0; // fallback: equal if unparseable
        }
    }
}
