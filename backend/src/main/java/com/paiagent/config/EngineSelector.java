package com.paiagent.config;

import com.paiagent.engine.DagWorkflowEngine;
import com.paiagent.engine.LangGraphWorkflowEngine;
import com.paiagent.engine.WorkflowEngine;
import com.paiagent.model.entity.AppConfig;
import com.paiagent.repository.AppConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

/**
 * Runtime engine selector that allows switching between DAG and LangGraph engines
 * without restarting the application. Engine choice is persisted to database
 * so it survives restarts.
 */
@Component
public class EngineSelector {

    private static final Logger log = LoggerFactory.getLogger(EngineSelector.class);

    private static final String CONFIG_KEY = "engine.type";

    private final DagWorkflowEngine dagEngine;
    private final LangGraphWorkflowEngine langGraphEngine;
    private final AppConfigRepository configRepository;
    private volatile String activeEngine = "dag";

    public EngineSelector(DagWorkflowEngine dagEngine,
                          LangGraphWorkflowEngine langGraphEngine,
                          AppConfigRepository configRepository) {
        this.dagEngine = dagEngine;
        this.langGraphEngine = langGraphEngine;
        this.configRepository = configRepository;
    }

    @PostConstruct
    public void loadFromDatabase() {
        try {
            Optional<AppConfig> config = configRepository.findById(CONFIG_KEY);
            if (config.isPresent()) {
                String stored = config.get().getConfigValue();
                if ("langgraph".equalsIgnoreCase(stored) || "dag".equalsIgnoreCase(stored)) {
                    this.activeEngine = stored.toLowerCase();
                    log.info("Loaded engine type from database: {}", activeEngine);
                    return;
                }
            }
            log.info("No persisted engine config found, using default: {}", activeEngine);
        } catch (Exception e) {
            log.warn("Failed to load engine config from database (table may not exist yet), using default: {}",
                activeEngine, e);
        }
    }

    /**
     * Return the currently active workflow engine.
     */
    public WorkflowEngine getActiveEngine() {
        if ("langgraph".equalsIgnoreCase(activeEngine)) {
            return langGraphEngine;
        }
        return dagEngine;
    }

    /**
     * Return the current engine type identifier.
     */
    public String getEngineType() {
        return activeEngine;
    }

    /**
     * Switch the active engine at runtime and persist the choice.
     * Falls back to DAG if the requested type is invalid.
     */
    public void setEngineType(String type) {
        if ("langgraph".equalsIgnoreCase(type)) {
            this.activeEngine = "langgraph";
            log.info("Engine switched to: langgraph");
        } else if ("dag".equalsIgnoreCase(type)) {
            this.activeEngine = "dag";
            log.info("Engine switched to: dag");
        } else {
            log.warn("Unknown engine type '{}', keeping current: {}", type, activeEngine);
            return;
        }
        persistEngineType();
    }

    private void persistEngineType() {
        try {
            AppConfig config = configRepository.findById(CONFIG_KEY).orElse(new AppConfig());
            config.setConfigKey(CONFIG_KEY);
            config.setConfigValue(activeEngine);
            configRepository.save(config);
        } catch (Exception e) {
            log.warn("Failed to persist engine config to database: {}", e.getMessage());
        }
    }
}
