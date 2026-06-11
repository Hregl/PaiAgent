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
}
