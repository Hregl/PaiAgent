package com.paiagent.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Wraps a NodeExecutor with retry and timeout capabilities.
 * Configured via node data fields: retryCount, retryDelayMs, timeoutMs.
 */
public class RetryableExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryableExecutor.class);

    private final NodeExecutor delegate;
    private final RetryConfig config;

    public RetryableExecutor(NodeExecutor delegate, RetryConfig config) {
        this.delegate = delegate;
        this.config = config;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) throws Exception {
        int maxAttempts = Math.max(1, config.retryCount() + 1);
        long delay = config.retryDelayMs();

        Exception lastException = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                if (config.timeoutMs() > 0) {
                    return executeWithTimeout(nodeData, context);
                }
                return delegate.execute(nodeData, context);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Node execution interrupted", e);
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts - 1) {
                    log.warn("Node execution failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt + 1, maxAttempts, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry sleep interrupted", ie);
                    }
                    delay = delay * 2;
                }
            }
        }
        throw new RuntimeException(
            "Node execution failed after " + maxAttempts + " attempts", lastException);
    }

    private Map<String, Object> executeWithTimeout(Map<String, Object> nodeData,
                                                    ExecutionContext context) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Map<String, Object>> future = executor.submit(() -> {
                try {
                    return delegate.execute(nodeData, context);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });
            return future.get(config.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException(
                "Node execution timed out after " + config.timeoutMs() + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException && cause.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException("Node execution failed: " + e.getMessage(), e);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Extract retry configuration from node data.
     * Returns null if no retry/timeout is configured.
     */
    public static RetryConfig fromNodeData(Map<String, Object> nodeData) {
        int retryCount = nodeData.get("retryCount") instanceof Number
            ? ((Number) nodeData.get("retryCount")).intValue() : 0;
        int retryDelayMs = nodeData.get("retryDelayMs") instanceof Number
            ? ((Number) nodeData.get("retryDelayMs")).intValue() : 1000;
        int timeoutMs = nodeData.get("timeoutMs") instanceof Number
            ? ((Number) nodeData.get("timeoutMs")).intValue() : 0;

        if (retryCount == 0 && timeoutMs == 0) {
            return null;
        }
        return new RetryConfig(retryCount, Math.max(100, retryDelayMs), timeoutMs);
    }
}
