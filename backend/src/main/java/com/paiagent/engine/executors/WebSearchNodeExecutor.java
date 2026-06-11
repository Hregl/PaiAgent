package com.paiagent.engine.executors;

import com.paiagent.engine.ExecutionContext;
import com.paiagent.engine.NodeExecutor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebSearchNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(WebSearchNodeExecutor.class);
    private static final String SEARCH_URL = "https://html.duckduckgo.com/html/?q=";

    private final RestClient restClient = RestClient.builder().build();

    @Override
    public Map<String, Object> execute(Map<String, Object> nodeData, ExecutionContext context) {
        String query = context.resolveTemplate((String) nodeData.getOrDefault("query", ""));
        int maxResults = nodeData.get("maxResults") instanceof Number
            ? ((Number) nodeData.get("maxResults")).intValue()
            : 5;

        if (query.isEmpty()) {
            return Map.of("error", "Query is required");
        }

        List<Map<String, String>> results = new ArrayList<>();

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String html = restClient.get()
                .uri(SEARCH_URL + encodedQuery)
                .header("User-Agent", "Mozilla/5.0 (compatible; PaiAgent/1.0)")
                .retrieve()
                .body(String.class);

            Document doc = Jsoup.parse(html);
            Elements resultElements = doc.select(".result");

            for (Element el : resultElements) {
                if (results.size() >= maxResults) break;

                Element titleEl = el.selectFirst(".result__title a");
                Element snippetEl = el.selectFirst(".result__snippet");
                Element urlEl = el.selectFirst(".result__url");

                String title = titleEl != null ? titleEl.text().trim() : "";
                String snippet = snippetEl != null ? snippetEl.text().trim() : "";
                String url = "";
                if (urlEl != null) {
                    url = urlEl.text().trim();
                } else if (titleEl != null) {
                    url = titleEl.attr("href");
                }

                if (!title.isEmpty()) {
                    results.add(Map.of("title", title, "url", url, "snippet", snippet));
                }
            }

            log.info("Web search '{}' returned {} results", query, results.size());
        } catch (Exception e) {
            log.error("Web search failed for '{}': {}", query, e.getMessage());
            return Map.of("error", e.getMessage());
        }

        Map<String, Object> output = new HashMap<>();
        output.put("results", results);
        output.put("query", query);
        output.put("count", results.size());
        return output;
    }
}
