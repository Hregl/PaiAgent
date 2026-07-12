package com.paiagent.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utility for extracting JSON from LLM responses that may be
 * wrapped in markdown code blocks or contain surrounding text.
 */
public final class JsonExtractor {

    private static final Pattern MD_CODE_BLOCK = Pattern.compile(
        "```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);

    private JsonExtractor() {}

    /**
     * Extract the outermost JSON object from a potentially messy LLM response.
     * Handles markdown code fences and surrounding prose.
     */
    public static String extract(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }
        Matcher matcher = MD_CODE_BLOCK.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
}
