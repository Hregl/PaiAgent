package com.paiagent.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paiagent.engine.executors.DecomposerExecutor;
import com.paiagent.model.dto.ApiResponse;
import com.paiagent.model.dto.DecomposeRequest;
import com.paiagent.model.dto.WorkflowDTO;
import com.paiagent.model.entity.User;
import com.paiagent.model.entity.Workflow;
import com.paiagent.repository.UserRepository;
import com.paiagent.repository.WorkflowRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowRepository workflowRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final DecomposerExecutor decomposerExecutor;

    public WorkflowController(WorkflowRepository workflowRepository, UserRepository userRepository,
                              ObjectMapper objectMapper, DecomposerExecutor decomposerExecutor) {
        this.workflowRepository = workflowRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.decomposerExecutor = decomposerExecutor;
    }

    @GetMapping
    public ApiResponse<List<Workflow>> list() {
        Long userId = getCurrentUserId();
        List<Workflow> workflows = workflowRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return ApiResponse.success(workflows);
    }

    @GetMapping("/{id}")
    public ApiResponse<Workflow> get(@PathVariable String id) {
        Workflow workflow = workflowRepository.findById(id).orElse(null);
        if (workflow == null) {
            return ApiResponse.error(404, "Workflow not found");
        }
        return ApiResponse.success(workflow);
    }

    @PostMapping
    public ApiResponse<Workflow> create(@Valid @RequestBody WorkflowDTO dto) throws JsonProcessingException {
        Workflow workflow = new Workflow();
        workflow.setId(UUID.randomUUID().toString());
        workflow.setName(dto.getName());
        workflow.setUserId(getCurrentUserId());
        workflow.setDefinition(objectMapper.writeValueAsString(dto.getDefinition()));
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setUpdatedAt(LocalDateTime.now());
        workflowRepository.save(workflow);
        return ApiResponse.success(workflow);
    }

    @PutMapping("/{id}")
    public ApiResponse<Workflow> update(@PathVariable String id, @Valid @RequestBody WorkflowDTO dto)
            throws JsonProcessingException {
        Workflow workflow = workflowRepository.findById(id).orElse(null);
        if (workflow == null) {
            return ApiResponse.error(404, "Workflow not found");
        }
        workflow.setName(dto.getName());
        workflow.setDefinition(objectMapper.writeValueAsString(dto.getDefinition()));
        workflow.setUpdatedAt(LocalDateTime.now());
        workflowRepository.save(workflow);
        return ApiResponse.success(workflow);
    }

    @PostMapping("/decompose")
    public ApiResponse<Object> decompose(@Valid @RequestBody DecomposeRequest request) {
        String provider = request.getProvider() != null ? request.getProvider() : "deepseek";
        String model = request.getModel() != null ? request.getModel() : "deepseek-chat";
        String apiKey = request.getApiKey();
        String apiBaseUrl = request.getApiBaseUrl();

        DecomposerExecutor.DecompositionResult result =
            decomposerExecutor.decompose(request.getTaskDescription(), provider, model, apiKey, apiBaseUrl);

        if (!result.success) {
            return ApiResponse.error(400, result.error);
        }

        return ApiResponse.success(Map.of("phases", result.phases));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        workflowRepository.deleteById(id);
        return ApiResponse.success(null);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (String) auth.getPrincipal();
        User user = userRepository.findByUsername(username).orElseThrow();
        return user.getId();
    }
}
