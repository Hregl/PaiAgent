package com.paiagent.engine;

import com.paiagent.engine.executors.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class NodeExecutorFactory {

    private final Map<String, NodeExecutor> executors = new HashMap<>();

    public NodeExecutorFactory(LLMNodeExecutor llmExecutor, TTSNodeExecutor ttsExecutor,
                              ConditionNodeExecutor conditionExecutor,
                              DecomposerExecutor decomposerExecutor,
                              JudgeNodeExecutor judgeExecutor) {
        executors.put("input", new InputNodeExecutor());
        executors.put("output", new OutputNodeExecutor());
        executors.put("llm", llmExecutor);
        executors.put("tts", ttsExecutor);
        executors.put("condition", conditionExecutor);
        executors.put("decomposer", decomposerExecutor);
        executors.put("judge", judgeExecutor);
    }

    public NodeExecutor getExecutor(String nodeType) {
        NodeExecutor executor = executors.get(nodeType);
        if (executor == null) {
            throw new IllegalArgumentException("Unknown node type: " + nodeType);
        }
        return executor;
    }
}
