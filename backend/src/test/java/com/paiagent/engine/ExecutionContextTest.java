package com.paiagent.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionContextTest {

    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ExecutionContext();
    }

    @Test
    void setAndGetNodeOutput() {
        ctx.setNodeOutput("node_1", "text", "hello");
        assertEquals("hello", ctx.getNodeOutput("node_1", "text"));
    }

    @Test
    void setNodeOutputsBulk() {
        ctx.setNodeOutputs("node_1", Map.of("text", "hello", "score", 95));
        assertEquals("hello", ctx.getNodeOutput("node_1", "text"));
        assertEquals(95, ctx.getNodeOutput("node_1", "score"));
    }

    @Test
    void getMissingNodeReturnsNull() {
        assertNull(ctx.getNodeOutput("nonexistent", "key"));
    }

    @Test
    void getAllOutputsReturnsCopy() {
        ctx.setNodeOutput("a", "x", 1);
        Map<String, Map<String, Object>> all = ctx.getAllOutputs();
        assertEquals(1, all.size());
        assertTrue(all.containsKey("a"));
    }

    @Test
    void resolveTemplateWithValidReference() {
        ctx.setNodeOutput("llm_1", "output", "你好世界");
        String result = ctx.resolveTemplate("翻译结果：{{llm_1.output}}");
        assertEquals("翻译结果：你好世界", result);
    }

    @Test
    void resolveTemplateWithMissingReference() {
        String result = ctx.resolveTemplate("{{missing.output}}");
        assertEquals("", result);
    }

    @Test
    void resolveTemplateWithNullReturnsEmpty() {
        assertEquals("", ctx.resolveTemplate(null));
    }

    @Test
    void resolveTemplateWithNoPlaceholders() {
        String result = ctx.resolveTemplate("plain text");
        assertEquals("plain text", result);
    }

    @Test
    void resolveTemplateWithMultipleReferences() {
        ctx.setNodeOutput("a", "x", "foo");
        ctx.setNodeOutput("b", "y", "bar");
        String result = ctx.resolveTemplate("{{a.x}} and {{b.y}}");
        assertEquals("foo and bar", result);
    }

    @Test
    void resolveTemplateWithSingleKeyFallsBackToOutputKey() {
        ctx.setNodeOutput("input_1", "output", "test value");
        String result = ctx.resolveTemplate("{{input_1}}");
        assertEquals("test value", result);
    }
}
