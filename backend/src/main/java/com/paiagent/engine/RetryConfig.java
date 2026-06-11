package com.paiagent.engine;

/**
 * Immutable retry and timeout configuration for a workflow node.
 */
public record RetryConfig(int retryCount, int retryDelayMs, int timeoutMs) {
    public RetryConfig {
        if (retryCount < 0) throw new IllegalArgumentException("retryCount must be >= 0");
        if (retryDelayMs < 100) throw new IllegalArgumentException("retryDelayMs must be >= 100");
        if (timeoutMs < 0) throw new IllegalArgumentException("timeoutMs must be >= 0");
    }
}
