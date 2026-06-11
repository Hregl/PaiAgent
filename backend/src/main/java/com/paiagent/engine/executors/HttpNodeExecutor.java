package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HttpNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpNodeExecutor.class);
    private static final int MAX_BODY_LENGTH = 102400; // 100KB

    private final RestClient restClient = RestClient.builder().build();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) {
        String method = ((String) nodeData.getOrDefault("method", "GET")).toUpperCase();
        String url = context.resolveTemplate((String) nodeData.getOrDefault("url", ""));
        int timeout = nodeData.get("timeout") instanceof Number
            ? ((Number) nodeData.get("timeout")).intValue()
            : 10000;

        if (url.isEmpty()) {
            return Map.of("error", "URL is required");
        }

        try {
            RestClient.RequestBodySpec spec = restClient
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(URI.create(url));

            // Add headers
            Object headersObj = nodeData.get("headers");
            if (headersObj instanceof List<?> headers) {
                for (Object h : headers) {
                    if (h instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> headerMap = (Map<String, Object>) h;
                        String key = (String) headerMap.get("key");
                        Object valueObj = headerMap.getOrDefault("value", "");
                        String value = context.resolveTemplate(valueObj != null ? valueObj.toString() : "");
                        if (key != null && !key.isEmpty()) {
                            spec.header(key, value);
                        }
                    }
                }
            }

            // Add body for POST/PUT
            String body = context.resolveTemplate((String) nodeData.getOrDefault("body", ""));
            if (!body.isEmpty() && ("POST".equals(method) || "PUT".equals(method))) {
                spec.body(body);
            }

            long start = System.currentTimeMillis();
            org.springframework.http.ResponseEntity<String> response = spec.retrieve()
                .toEntity(String.class);
            long durationMs = System.currentTimeMillis() - start;

            String responseBody = response.getBody() != null ? response.getBody() : "";
            if (responseBody.length() > MAX_BODY_LENGTH) {
                responseBody = responseBody.substring(0, MAX_BODY_LENGTH) + "...(truncated)";
            }

            Map<String, String> responseHeaders = new HashMap<>();
            response.getHeaders().forEach((k, v) -> responseHeaders.put(k, String.join(", ", v)));

            Map<String, Object> result = new HashMap<>();
            result.put("status", response.getStatusCode().value());
            result.put("body", responseBody);
            result.put("headers", responseHeaders);
            result.put("durationMs", durationMs);

            log.info("HTTP {} {} -> {} ({}ms)", method, url, response.getStatusCode().value(), durationMs);
            return result;
        } catch (Exception e) {
            log.error("HTTP {} {} failed: {}", method, url, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("error", e.getMessage());
            result.put("status", 0);
            return result;
        }
    }
}
