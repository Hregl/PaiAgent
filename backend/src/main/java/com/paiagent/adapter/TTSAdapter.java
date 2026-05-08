package com.paiagent.adapter;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    /**
     * Synthesize text to audio.
     * @param text the text to synthesize
     * @param voiceId the voice to use
     * @return URL path to the generated audio file
     */
    public String synthesize(String text, String voiceId) throws Exception {
        // Ensure audio directory exists
        File audioDir = new File(audioPath);
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }

        String fileName = UUID.randomUUID().toString() + ".mp3";
        File outputFile = new File(audioDir, fileName);

        if (baseUrl != null && !baseUrl.isEmpty()) {
            // Call real TTS API
            callTTSApi(text, voiceId, outputFile);
        } else {
            // Fallback: generate a placeholder audio file for demo purposes
            generatePlaceholderAudio(text, outputFile);
        }

        return "/files/audio/" + fileName;
    }

    private void callTTSApi(String text, String voiceId, File outputFile) throws Exception {
        // Generic TTS API call - adjust based on your provider
        String requestBody = String.format(
                "{\"text\":\"%s\",\"voice\":\"%s\",\"format\":\"mp3\"}",
                text.replace("\"", "\\\"").replace("\n", "\\n"),
                voiceId
        );

        Request request = new Request.Builder()
                .url(baseUrl + "/synthesize")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("TTS API error: " + response.code());
            }
            if (response.body() != null) {
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(response.body().bytes());
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
}
