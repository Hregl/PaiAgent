package com.paiagent.adapter;

import java.util.Map;

/**
 * Unified interface for all LLM providers.
 */
public interface LLMAdapter {
    /**
     * Send a chat message to the LLM.
     * @param prompt the user prompt
     * @param config configuration including model, temperature, maxTokens
     * @return the LLM response text
     */
    String chat(String prompt, Map<String, Object> config) throws Exception;
}
