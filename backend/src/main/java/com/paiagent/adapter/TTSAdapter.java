package com.paiagent.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
        String nodeBaseUrl = baseUrl; // use global baseUrl for now

        if ((nodeApiKey != null && !nodeApiKey.isBlank()) || (baseUrl != null && !baseUrl.isEmpty())) {
            // Call real TTS API
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

        // Build request body for TTS API
        String requestBody = String.format(
                "{\"model\":\"%s\",\"input\":{\"text\":\"%s\"},\"parameters\":{\"voice\":\"%s\",\"language_type\":\"%s\"}}",
                model,
                text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"),
                voice,
                languageType
        );

        byte[] audioBytes = restClient.post()
                .uri(baseUrl + "/synthesize")
                .header("Authorization", "Bearer " + effectiveApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(byte[].class);

        if (audioBytes != null) {
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(audioBytes);
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
}
