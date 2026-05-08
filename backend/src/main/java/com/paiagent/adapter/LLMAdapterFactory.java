package com.paiagent.adapter;

import com.paiagent.adapter.impl.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
public class LLMAdapterFactory {

    @Value("${llm.deepseek.api-key:}")
    private String deepseekApiKey;
    @Value("${llm.deepseek.base-url:}")
    private String deepseekBaseUrl;

    @Value("${llm.qwen.api-key:}")
    private String qwenApiKey;
    @Value("${llm.qwen.base-url:}")
    private String qwenBaseUrl;

    @Value("${llm.chatglm.api-key:}")
    private String chatglmApiKey;
    @Value("${llm.chatglm.base-url:}")
    private String chatglmBaseUrl;

    @Value("${llm.aiping.api-key:}")
    private String aipingApiKey;
    @Value("${llm.aiping.base-url:}")
    private String aipingBaseUrl;

    private final Map<String, LLMAdapter> adapters = new HashMap<>();

    @PostConstruct
    public void init() {
        adapters.put("deepseek", new DeepSeekAdapter(deepseekApiKey, deepseekBaseUrl));
        adapters.put("qwen", new QwenAdapter(qwenApiKey, qwenBaseUrl));
        adapters.put("chatglm", new ChatGLMAdapter(chatglmApiKey, chatglmBaseUrl));
        adapters.put("aiping", new AIPingAdapter(aipingApiKey, aipingBaseUrl));
    }

    public LLMAdapter getAdapter(String provider) {
        LLMAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        }
        return adapter;
    }
}
