package com.paiagent.model.dto;

import jakarta.validation.constraints.NotBlank;

public class DecomposeRequest {
    @NotBlank(message = "taskDescription is required")
    private String taskDescription;

    private String provider;
    private String model;
    private String apiKey;
    private String apiBaseUrl;

    public String getTaskDescription() { return taskDescription; }
    public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
}
