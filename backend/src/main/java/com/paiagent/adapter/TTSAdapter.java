package com.paiagent.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * TTS (Text-to-Speech) adapter for audio synthesis.
 * Supports external TTS API integration.
 */
@Component
public class TTSAdapter {

    @Value("${tts.api-key:}")
    private String apiKey;

    @Value("${tts.base-url:}")
    private String baseUrl;

    @Value("${storage.audio-path:./data/audio}")
    private String audioPath;

    private final RestClient restClient = RestClient.builder().build();

    /**
     * Synthesize text to audio.
     * @param text the text to synthesize
     * @param config configuration map with optional apiKey, model, voice, languageType
     * @return URL path to the generated audio file
     */
    public String synthesize(String text, Map<String, String> config) throws Exception {
        // Ensure audio directory exists
        File audioDir = new File(audioPath);
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }

        String fileName = UUID.randomUUID().toString() + ".mp3";
        File outputFile = new File(audioDir, fileName);

        String nodeApiKey = config.getOrDefault("apiKey", "");
        String effectiveBaseUrl = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl
                : getDefaultTtsBaseUrl();

        if ((nodeApiKey != null && !nodeApiKey.isBlank()) || (baseUrl != null && !baseUrl.isEmpty())) {
            // Call real TTS API
            config.put("effectiveBaseUrl", effectiveBaseUrl);
            callTTSApi(text, config, outputFile);
        } else {
            // Fallback: generate a placeholder audio file for demo purposes
            generatePlaceholderAudio(text, outputFile);
        }

        return "/files/audio/" + fileName;
    }

    private void callTTSApi(String text, Map<String, String> config, File outputFile) throws Exception {
        String model = config.getOrDefault("model", "qwen3-tts-flash");
        String voice = config.getOrDefault("voice", "Cherry");
        String languageType = config.getOrDefault("languageType", "Auto");
        String nodeApiKey = config.getOrDefault("apiKey", "");
        String effectiveApiKey = (nodeApiKey != null && !nodeApiKey.isBlank()) ? nodeApiKey : apiKey;
        String apiBaseUrl = config.getOrDefault("effectiveBaseUrl", "");

        // Build request body for TTS API
        // voice and language_type go inside input (not parameters) per DashScope multimodal-generation spec
        String requestBody = String.format(
                "{\"model\":\"%s\",\"input\":{\"text\":\"%s\",\"voice\":\"%s\",\"language_type\":\"%s\"}}",
                model,
                text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"),
                voice,
                languageType
        );

        // DashScope TTS returns JSON with output.audio.raw (base64) or output.audio.url
        // Try parsing as JSON first, fall back to raw bytes
        String response = restClient.post()
                .uri(apiBaseUrl)
                .header("Authorization", "Bearer " + effectiveApiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        if (response != null) {
            // Parse DashScope multimodal-generation TTS response
            // Format: {"output":{"choices":[{"message":{"content":[{"audio":"base64..."}]}}]}}
            // Or:     {"output":{"audio":{"url":"..."}}} or {"output":{"audio":{"data":"base64..."}}}
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                var result = mapper.readValue(response, java.util.Map.class);
                var output = (java.util.Map<String, Object>) result.get("output");
                if (output != null) {
                    // Try choices[0].message.content[0].audio (multimodal-generation format)
                    var choices = (java.util.List<Object>) output.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        var choice = (java.util.Map<String, Object>) choices.get(0);
                        var message = (java.util.Map<String, Object>) choice.get("message");
                        if (message != null) {
                            var content = (java.util.List<Object>) message.get("content");
                            if (content != null && !content.isEmpty()) {
                                @SuppressWarnings("unchecked")
                                var contentItem = (java.util.Map<String, Object>) content.get(0);
                                Object audioData = contentItem.get("audio");
                                if (audioData instanceof byte[] audioBytes) {
                                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                        fos.write(audioBytes);
                                    }
                                    return;
                                } else if (audioData instanceof String audioStr) {
                                    byte[] decoded = java.util.Base64.getDecoder().decode(audioStr);
                                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                        fos.write(decoded);
                                    }
                                    return;
                                }
                            }
                        }
                    }
                    // Try output.audio.url
                    var audio = (java.util.Map<String, Object>) output.get("audio");
                    if (audio != null) {
                        if (audio.get("url") != null) {
                            String audioUrl = audio.get("url").toString();
                            byte[] audioBytes = restClient.get()
                                    .uri(audioUrl)
                                    .retrieve()
                                    .body(byte[].class);
                            if (audioBytes != null) {
                                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                    fos.write(audioBytes);
                                }
                                return;
                            }
                        }
                        if (audio.get("data") != null) {
                            byte[] decoded = java.util.Base64.getDecoder().decode(audio.get("data").toString());
                            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                fos.write(decoded);
                            }
                            return;
                        }
                    }
                }
            } catch (Exception jsonEx) {
                // Not JSON or unexpected format — treat response as raw audio bytes
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(response.getBytes());
                }
            }
        }
    }

    private void generatePlaceholderAudio(String text, File outputFile) throws IOException {
        // Generate a minimal valid MP3 file as placeholder
        // In production, this would be replaced by actual TTS API call
        // This creates a tiny silent MP3 frame for testing purposes
        byte[] silentMp3 = new byte[]{
            (byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        };

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            // Write multiple frames for a longer duration
            for (int i = 0; i < 100; i++) {
                fos.write(silentMp3);
            }
        }
    }

    private String getDefaultTtsBaseUrl() {
        return "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    }
}
