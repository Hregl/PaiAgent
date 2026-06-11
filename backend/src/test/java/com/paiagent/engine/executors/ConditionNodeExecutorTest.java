package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionNodeExecutorTest {

    private ConditionNodeExecutor executor;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        executor = new ConditionNodeExecutor();
        ctx = new ExecutionContext();
    }

    @Test
    void equalsReturnsTrue() {
        ctx.setNodeOutput("n1", "text", "match");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "equals", "rightValue", "match"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void equalsReturnsFalse() {
        ctx.setNodeOutput("n1", "text", "different");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "equals", "rightValue", "match"), ctx);
        assertEquals("false", result.get("branch"));
    }

    @Test
    void containsReturnsTrue() {
        ctx.setNodeOutput("n1", "text", "hello world");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "contains", "rightValue", "world"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void startsWithReturnsTrue() {
        ctx.setNodeOutput("n1", "text", "prefix-suffix");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "starts_with", "rightValue", "prefix"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void isEmptyReturnsTrue() {
        ctx.setNodeOutput("n1", "text", "");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "is_empty", "rightValue", ""), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void isNotEmptyReturnsTrue() {
        ctx.setNodeOutput("n1", "text", "something");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "is_not_empty", "rightValue", ""), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void notEqualsReturnsTrue() {
        ctx.setNodeOutput("n1", "text", "a");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "not_equals", "rightValue", "b"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void resolvesTemplateStyleLeftRef() {
        ctx.setNodeOutput("n1", "text", "actual");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "{{n1.text}}", "operator", "equals", "rightValue", "actual"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void notContainsReturnsTrue() {
        ctx.setNodeOutput("n1", "text", "hello world");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "not_contains", "rightValue", "xyz"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void notContainsReturnsFalse() {
        ctx.setNodeOutput("n1", "text", "hello world");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "not_contains", "rightValue", "hello"), ctx);
        assertEquals("false", result.get("branch"));
    }

    @Test
    void greaterThanReturnsTrue() {
        ctx.setNodeOutput("n1", "score", "10");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.score", "operator", "greater_than", "rightValue", "5"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void greaterThanReturnsFalse() {
        ctx.setNodeOutput("n1", "score", "3");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.score", "operator", "greater_than", "rightValue", "5"), ctx);
        assertEquals("false", result.get("branch"));
    }

    @Test
    void lessThanReturnsTrue() {
        ctx.setNodeOutput("n1", "score", "3");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.score", "operator", "less_than", "rightValue", "5"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void greaterOrEqualReturnsTrue() {
        ctx.setNodeOutput("n1", "score", "5");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.score", "operator", "greater_or_equal", "rightValue", "5"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void lessOrEqualReturnsTrue() {
        ctx.setNodeOutput("n1", "score", "5");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.score", "operator", "less_or_equal", "rightValue", "5"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void matchesRegexReturnsTrue() {
        ctx.setNodeOutput("n1", "text", "ABC-1234");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "matches_regex", "rightValue", "\\w{3}-\\d{4}"), ctx);
        assertEquals("true", result.get("branch"));
    }

    @Test
    void matchesRegexReturnsFalseForNoMatch() {
        ctx.setNodeOutput("n1", "text", "hello");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "matches_regex", "rightValue", "\\d+"), ctx);
        assertEquals("false", result.get("branch"));
    }

    @Test
    void invalidRegexReturnsFalse() {
        ctx.setNodeOutput("n1", "text", "data");
        Map<String, Object> result = executor.execute(
            Map.of("leftRef", "n1.text", "operator", "matches_regex", "rightValue", "[unclosed"), ctx);
        assertEquals("false", result.get("branch"));
    }
}
