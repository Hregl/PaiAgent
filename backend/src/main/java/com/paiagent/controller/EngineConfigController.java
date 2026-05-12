package com.paiagent.controller;

import com.paiagent.config.EngineSelector;
import com.paiagent.model.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for querying and switching the workflow engine at runtime.
 */
@RestController
@RequestMapping("/api/config")
public class EngineConfigController {

    private final EngineSelector engineSelector;

    public EngineConfigController(EngineSelector engineSelector) {
        this.engineSelector = engineSelector;
    }

    @GetMapping("/engine")
    public ApiResponse<Map<String, String>> getEngine() {
        return ApiResponse.success(Map.of("engineType", engineSelector.getEngineType()));
    }

    @PutMapping("/engine")
    public ApiResponse<Map<String, String>> setEngine(@RequestBody Map<String, String> body) {
        String type = body.get("engineType");
        if (type == null || (!"dag".equalsIgnoreCase(type) && !"langgraph".equalsIgnoreCase(type))) {
            return ApiResponse.error(400, "Invalid engine type. Must be 'dag' or 'langgraph'");
        }
        engineSelector.setEngineType(type.toLowerCase());
        return ApiResponse.success(Map.of("engineType", engineSelector.getEngineType()));
    }
}
