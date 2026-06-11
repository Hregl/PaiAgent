package com.paiagent.repository;

import com.paiagent.model.entity.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigRepository extends JpaRepository<AppConfig, String> {
}
