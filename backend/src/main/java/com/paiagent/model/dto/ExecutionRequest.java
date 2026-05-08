package com.paiagent.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ExecutionRequest {
    @NotBlank
    private String input;
}
