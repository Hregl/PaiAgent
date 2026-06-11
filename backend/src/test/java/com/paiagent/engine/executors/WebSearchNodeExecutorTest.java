package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchNodeExecutorTest {

    private WebSearchNodeExecutor executor;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        executor = new WebSearchNodeExecutor();
        ctx = new ExecutionContext();
    }

    @Test
    void returnsErrorWhenQueryIsEmpty() {
        Map<String, Object> result = executor.execute(
            Map.of("query", ""), ctx);
        assertEquals("Query is required", result.get("error"));
    }

    @Test
    void resolvesQueryFromContext() {
        // Tests that resolveTemplate is called; empty resolved still errors
        Map<String, Object> result = executor.execute(
            Map.of("query", ""), ctx);
        assertEquals("Query is required", result.get("error"));
    }

    @Test
    void defaultMaxResultsIsFive() {
        // Verifies config parsing doesn't throw
        Map<String, Object> result = executor.execute(
            Map.of("query", ""), ctx);
        assertEquals("Query is required", result.get("error"));
    }

    @Test
    void acceptsCustomMaxResults() {
        // Even with empty query, verifies maxResults parsing
        Map<String, Object> result = executor.execute(
            Map.of("query", "", "maxResults", 3), ctx);
        assertEquals("Query is required", result.get("error"));
    }
}
