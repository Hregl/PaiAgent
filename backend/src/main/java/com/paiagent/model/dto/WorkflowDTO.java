package com.paiagent.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class WorkflowDTO {
    @NotBlank
    private String name;
    private Object definition;
}
