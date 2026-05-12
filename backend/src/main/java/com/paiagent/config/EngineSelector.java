package com.paiagent.config;

import com.paiagent.engine.DagWorkflowEngine;
import com.paiagent.engine.LangGraphWorkflowEngine;
import com.paiagent.engine.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runtime engine selector that allows switching between DAG and LangGraph engines
 * without restarting the application.
 */
@Component
public class EngineSelector {

    private static final Logger log = LoggerFactory.getLogger(EngineSelector.class);

    private final DagWorkflowEngine dagEngine;
    private final LangGraphWorkflowEngine langGraphEngine;
    private volatile String activeEngine = "dag";

    public EngineSelector(DagWorkflowEngine dagEngine,
                          LangGraphWorkflowEngine langGraphEngine) {
        this.dagEngine = dagEngine;
        this.langGraphEngine = langGraphEngine;
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
     * Switch the active engine at runtime.
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
        }
    }
}
