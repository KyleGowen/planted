package com.planted.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.Map;

/**
 * Client for OpenAI image generation (DALL-E 3).
 * Used for creating illustrated / stylized plant images.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiImageGenerationClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${planted.openai.api-key:}")
    private String apiKey;

    @Value("${planted.openai.image-model:dall-e-3}")
    private String imageModel;

    private static final String IMAGES_API_URL = "https://api.openai.com/v1/images/generations";

    /**
     * Generate a stylized illustrated image of a plant.
     *
     * @param genus    plant genus for prompt context
     * @param species  plant species for prompt context
     * @param location optional location context
     * @return raw PNG bytes of the generated image
     */
    public byte[] generateIllustratedPlantImage(String genus, String species, String location) {
        String speciesName = (genus != null ? genus : "plant") + (species != null ? " " + species : "");
        String locationContext = location != null ? " in a " + location : "";
        String prompt = String.format(
                "A clean, elegant botanical illustration of a %s%s. " +
                "Soft watercolor style with natural green tones, white background, " +
                "premium botanical journal aesthetic. No text, no labels.",
                speciesName, locationContext
        );

        Map<String, Object> requestBody = Map.of(
                "model", imageModel,
                "prompt", prompt,
                "n", 1,
                "size", "1024x1024",
                "response_format", "b64_json"
        );

        try {
            String response = webClientBuilder.build()
                    .post()
                    .uri(IMAGES_API_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String b64 = root.path("data").get(0).path("b64_json").asText();
            return Base64.getDecoder().decode(b64);
        } catch (Exception e) {
            log.error("Image generation failed for {}", speciesName, e);
            throw new RuntimeException("Image generation failed: " + e.getMessage(), e);
        }
    }
}
