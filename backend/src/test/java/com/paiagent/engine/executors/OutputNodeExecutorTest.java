package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OutputNodeExecutorTest {

    private OutputNodeExecutor executor;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        executor = new OutputNodeExecutor();
        ctx = new ExecutionContext();
    }

    @Test
    void assemblesOutputsFromReferences() {
        ctx.setNodeOutput("llm_1", "output", "generated text");
        ctx.setNodeOutput("tts_1", "audioUrl", "/files/audio/test.wav");

        List<Map<String, String>> outputs = List.of(
            Map.of("paramName", "text", "paramType", "reference", "value", "{{llm_1.output}}"),
            Map.of("paramName", "audio", "paramType", "reference", "value", "{{tts_1.audioUrl}}")
        );

        Map<String, Object> result = executor.execute(
            Map.of("outputs", outputs), ctx);

        assertEquals("generated text", result.get("text"));
        assertEquals("/files/audio/test.wav", result.get("audio"));
    }

    @Test
    void handlesStaticInputValues() {
        List<Map<String, String>> outputs = List.of(
            Map.of("paramName", "status", "paramType", "input", "value", "done")
        );

        Map<String, Object> result = executor.execute(
            Map.of("outputs", outputs), ctx);

        assertEquals("done", result.get("status"));
    }

    @Test
    void resolvesResponseTemplateAgainstLocalResult() {
        ctx.setNodeOutput("llm_1", "output", "hello world");

        List<Map<String, String>> outputs = List.of(
            Map.of("paramName", "text", "paramType", "reference", "value", "{{llm_1.output}}")
        );

        Map<String, Object> result = executor.execute(
            Map.of("outputs", outputs, "responseTemplate", "Result: {{text}}"), ctx);

        assertEquals("Result: hello world", result.get("text"));
    }

    @Test
    void emptyOutputsListProducesOnlyText() {
        Map<String, Object> result = executor.execute(
            Map.of("outputs", List.of()), ctx);

        assertTrue(result.containsKey("text"));
    }

    @Test
    void supportsLegacyKeyRefFormat() {
        ctx.setNodeOutput("n1", "out", "legacy value");

        List<Map<String, String>> outputs = List.of(
            Map.of("key", "result", "ref", "{{n1.out}}")
        );

        Map<String, Object> result = executor.execute(
            Map.of("outputs", outputs), ctx);

        assertEquals("legacy value", result.get("result"));
    }
}
