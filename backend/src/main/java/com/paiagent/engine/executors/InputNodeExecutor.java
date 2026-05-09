package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;

import java.util.HashMap;
import java.util.Map;

public class InputNodeExecutor implements NodeExecutor {

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) {
        String userInput = (String) nodeData.getOrDefault("_userInput", "");
        String variableName = (String) nodeData.getOrDefault("variableName", "output");
        Boolean required = (Boolean) nodeData.getOrDefault("required", false);

        if (Boolean.TRUE.equals(required) && (userInput == null || userInput.isBlank())) {
            throw new IllegalArgumentException(
                "Input node '" + variableName + "' is required but received empty input");
        }

        Map<String, Object> output = new HashMap<>();
        output.put(variableName, userInput);
        return output;
    }
}
