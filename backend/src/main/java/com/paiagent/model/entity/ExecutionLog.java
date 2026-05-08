package com.paiagent.model.entity;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "execution_logs")
public class ExecutionLog {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "workflow_id", length = 36, nullable = false)
    private String workflowId;

    @Column(columnDefinition = "TEXT")
    private String input;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(length = 20)
    private String status;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "node_logs", columnDefinition = "TEXT")
    private String nodeLogs;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
