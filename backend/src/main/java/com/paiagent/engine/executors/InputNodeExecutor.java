package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;

import java.util.HashMap;
import java.util.Map;

public class InputNodeExecutor implements NodeExecutor {

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) {
        String userInput = (String) nodeData.getOrDefault("_userInput", "");
        Map<String, Object> output = new HashMap<>();
        output.put("output", userInput);
        return output;
    }
}
