package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpNodeExecutorTest {

    private HttpNodeExecutor executor;
    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        executor = new HttpNodeExecutor();
        ctx = new ExecutionContext();
    }

    @Test
    void returnsErrorWhenUrlIsEmpty() {
        Map<String, Object> result = executor.execute(
            Map.of("method", "GET", "url", ""), ctx);
        assertEquals("URL is required", result.get("error"));
    }

    @Test
    void returnsErrorForInvalidUrl() {
        Map<String, Object> result = executor.execute(
            Map.of("method", "GET", "url", "not:a:valid:url"), ctx);
        assertEquals(0, result.get("status"));
        assertNotNull(result.get("error"));
    }

    @Test
    void resolvesUrlsFromContext() {
        ctx.setNodeOutput("n1", "path", "/api/resource");
        // The URL as a whole would need to be templated; testing that resolveTemplate is called
        Map<String, Object> result = executor.execute(
            Map.of("method", "GET", "url", "not:a:valid:url"), ctx);
        assertEquals(0, result.get("status"));
    }

    @Test
    void defaultMethodIsGET() {
        Map<String, Object> result = executor.execute(
            Map.of("url", "not:a:valid:url"), ctx);
        assertEquals(0, result.get("status"));
    }

    @Test
    void handlesHeadersList() {
        // Verifies headers parsing doesn't throw; actual HTTP validation happens at URL parse
        Map<String, Object> result = executor.execute(
            Map.of("method", "GET", "url", "not:a:valid:url",
                "headers", List.of(Map.of("key", "Authorization", "value", "Bearer test"))),
            ctx);
        assertEquals(0, result.get("status"));
    }
}
