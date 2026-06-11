package com.paiagent.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryableExecutorTest {

    private ExecutionContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ExecutionContext();
    }

    @Test
    void retriesOnFailureThenSucceeds() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        NodeExecutor flaky = (data, context) -> {
            if (callCount.incrementAndGet() < 3) {
                throw new RuntimeException("fail " + callCount.get());
            }
            return Map.of("result", "success");
        };

        RetryConfig config = new RetryConfig(3, 100, 0);
        RetryableExecutor executor = new RetryableExecutor(flaky, config);

        Map<String, Object> result = executor.execute(Map.of(), ctx);

        assertEquals(3, callCount.get());
        assertEquals("success", result.get("result"));
    }

    @Test
    void failsAfterExhaustingRetries() {
        NodeExecutor alwaysFails = (data, context) -> {
            throw new RuntimeException("always fail");
        };

        RetryConfig config = new RetryConfig(2, 100, 0);
        RetryableExecutor executor = new RetryableExecutor(alwaysFails, config);

        Exception ex = assertThrows(RuntimeException.class,
            () -> executor.execute(Map.of(), ctx));
        assertTrue(ex.getMessage().contains("3 attempts"));
    }

    @Test
    void fromNodeDataReturnsNullWhenNoConfig() {
        Map<String, Object> nodeData = Map.of("prompt", "hello");
        assertNull(RetryableExecutor.fromNodeData(nodeData));
    }

    @Test
    void fromNodeDataExtractsRetryCount() {
        Map<String, Object> nodeData = Map.of(
            "retryCount", 2,
            "retryDelayMs", 500
        );
        RetryConfig config = RetryableExecutor.fromNodeData(nodeData);
        assertNotNull(config);
        assertEquals(2, config.retryCount());
        assertEquals(500, config.retryDelayMs());
    }

    @Test
    void fromNodeDataExtractsTimeoutOnly() {
        Map<String, Object> nodeData = Map.of("timeoutMs", 30000);
        RetryConfig config = RetryableExecutor.fromNodeData(nodeData);
        assertNotNull(config);
        assertEquals(0, config.retryCount());
        assertEquals(30000, config.timeoutMs());
    }
}
