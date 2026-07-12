package com.paiagent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads prompt templates from classpath files with variable substitution.
 * Templates use {key} placeholders that are replaced at call time.
 */
@Component
public class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Load a prompt template from classpath:prompts/{name}.txt,
     * substitute {key} placeholders with provided variables, and return the result.
     */
    public String render(String name, Map<String, String> variables) {
        String template = cache.computeIfAbsent(name, this::loadFromClasspath);
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String loadFromClasspath(String name) {
        String path = "prompts/" + name + ".txt";
        try {
            var resource = new ClassPathResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt template: {}", path, e);
            throw new IllegalStateException("Missing prompt template: " + path, e);
        }
    }
}
