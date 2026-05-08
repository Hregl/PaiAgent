package com.paiagent.repository;

import com.paiagent.model.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRepository extends JpaRepository<Workflow, String> {
    List<Workflow> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
