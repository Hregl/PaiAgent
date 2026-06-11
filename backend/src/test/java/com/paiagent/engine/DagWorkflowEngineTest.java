package com.paiagent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paiagent.engine.executors.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DagWorkflowEngineTest {

    @Mock
    private LLMNodeExecutor llmExecutor;
    @Mock
    private TTSNodeExecutor ttsExecutor;
    @Mock
    private ConditionNodeExecutor conditionExecutor;
    @Mock
    private DecomposerExecutor decomposerExecutor;
    @Mock
    private JudgeNodeExecutor judgeExecutor;
    @Mock
    private HttpNodeExecutor httpExecutor;
    @Mock
    private WebSearchNodeExecutor webSearchExecutor;

    private DagWorkflowEngine engine;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        NodeExecutorFactory factory = new NodeExecutorFactory(
            llmExecutor, ttsExecutor, conditionExecutor, decomposerExecutor, judgeExecutor,
            httpExecutor, webSearchExecutor);
        objectMapper = new ObjectMapper();
        engine = new DagWorkflowEngine(factory, objectMapper);
    }

    @Test
    void executeSimpleInputOutputWorkflow() throws Exception {
        String definition = """
        {
            "nodes": [
                {"id": "in", "type": "input", "data": {"label": "Input"}},
                {"id": "out", "type": "output", "data": {"label": "Output", "outputs": []}}
            ],
            "edges": [
                {"id": "e1", "source": "in", "target": "out"}
            ]
        }""";

        Map<String, Object> result = engine.execute(definition, "test input");

        assertNull(result.get("error"));
        assertNotNull(result.get("nodeLogs"));
        assertNotNull(result.get("output"));
    }

    @Test
    void executeWithLLMNode() throws Exception {
        when(llmExecutor.execute(any(), any()))
            .thenReturn(Map.of("output", "AI response"));

        String definition = """
        {
            "nodes": [
                {"id": "in", "type": "input", "data": {"label": "Input"}},
                {"id": "llm", "type": "llm", "data": {"label": "LLM", "prompt": "Hello"}},
                {"id": "out", "type": "output", "data": {"label": "Output", "outputs": []}}
            ],
            "edges": [
                {"id": "e1", "source": "in", "target": "llm"},
                {"id": "e2", "source": "llm", "target": "out"}
            ]
        }""";

        Map<String, Object> result = engine.execute(definition, "test");

        assertNull(result.get("error"));
        assertNotNull(result.get("output"));
    }

    @Test
    void rejectsConditionNodes() {
        String definition = """
        {
            "nodes": [
                {"id": "in", "type": "input", "data": {}},
                {"id": "cond", "type": "condition", "data": {}},
                {"id": "out", "type": "output", "data": {}}
            ],
            "edges": []
        }""";

        Exception ex = assertThrows(Exception.class,
            () -> engine.execute(definition, "test"));
        assertTrue(ex.getMessage().contains("LangGraph"));
    }

    @Test
    void rejectsEmptyNodes() {
        String definition = "{\"nodes\": [], \"edges\": []}";

        assertThrows(Exception.class,
            () -> engine.execute(definition, "test"));
    }

    @Test
    void nodeFailureReturnsPartialResult() throws Exception {
        when(llmExecutor.execute(any(), any()))
            .thenThrow(new RuntimeException("API error"));

        String definition = """
        {
            "nodes": [
                {"id": "in", "type": "input", "data": {}},
                {"id": "llm", "type": "llm", "data": {"prompt": "test"}},
                {"id": "out", "type": "output", "data": {"outputs": []}}
            ],
            "edges": [
                {"id": "e1", "source": "in", "target": "llm"},
                {"id": "e2", "source": "llm", "target": "out"}
            ]
        }""";

        Map<String, Object> result = engine.execute(definition, "test");

        assertEquals("FAILED", result.get("status"));
        assertNotNull(result.get("error"));
        assertNotNull(result.get("nodeLogs"));
    }

    @Test
    void topologicalOrderPreservesDependencies() throws Exception {
        when(llmExecutor.execute(any(), any()))
            .thenReturn(Map.of("output", "response"));

        String definition = """
        {
            "nodes": [
                {"id": "in", "type": "input", "data": {}},
                {"id": "llm_2", "type": "llm", "data": {"prompt": "second"}},
                {"id": "llm_1", "type": "llm", "data": {"prompt": "first"}},
                {"id": "out", "type": "output", "data": {"outputs": []}}
            ],
            "edges": [
                {"id": "e1", "source": "in", "target": "llm_1"},
                {"id": "e2", "source": "llm_1", "target": "llm_2"},
                {"id": "e3", "source": "llm_2", "target": "out"}
            ]
        }""";

        Map<String, Object> result = engine.execute(definition, "test");

        assertNull(result.get("error"));
        assertNotNull(result.get("output"));
    }
}
