package com.paiagent.repository;

import com.paiagent.model.entity.ExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, String> {
    List<ExecutionLog> findByWorkflowIdOrderByCreatedAtDesc(String workflowId);
}
