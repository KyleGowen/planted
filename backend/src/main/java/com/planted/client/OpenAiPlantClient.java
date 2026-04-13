package com.planted.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planted.entity.LlmPrompt;
import com.planted.entity.LlmRequest;
import com.planted.repository.LlmPromptRepository;
import com.planted.repository.LlmRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.LinkedHashMap;

/**
 * Client for the OpenAI Responses API.
 * Uses structured outputs / JSON schema for all plant analysis calls.
 * Every call is audited to llm_requests per Database-Prompt-Storage spec.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiPlantClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final LlmPromptRepository promptRepository;
    private final LlmRequestRepository requestRepository;

    @Value("${planted.openai.api-key:}")
    private String apiKey;

    @Value("${planted.openai.model:gpt-4o}")
    private String model;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/responses";

    /**
     * Perform plant registration / reanalysis.
     *
     * @param imageBase64 base64-encoded image data
     * @param mimeType    image mime type
     * @param goalsText   optional user goals
     * @param location    optional plant location
     * @param plantId     for audit logging
     * @param analysisId  for audit logging
     */
    public PlantAnalysisSchema analyzeRegistration(
            String imageBase64, String mimeType,
            String goalsText, String location,
            Long plantId, Long analysisId) {

        String promptKey = "plant_registration_analysis_v1";
        String systemPrompt = resolvePrompt(promptKey, "system");
        String userPrompt = renderUserPrompt(promptKey,
                Map.of("goals_text", Optional.ofNullable(goalsText).orElse(""),
                        "location", Optional.ofNullable(location).orElse("")));

        Map<String, Object> inputVariables = new HashMap<>();
        inputVariables.put("goals_text", goalsText);
        inputVariables.put("location", location);

        String responseText = callWithImage(systemPrompt, userPrompt, imageBase64, mimeType,
                promptKey, inputVariables, plantId, analysisId, registrationSchema());

        try {
            JsonNode root = objectMapper.readTree(responseText);
            // Extract structured output from response
            String outputText = extractOutputText(root);
            return objectMapper.readValue(outputText, PlantAnalysisSchema.class);
        } catch (Exception e) {
            log.error("Failed to parse plant analysis response", e);
            throw new RuntimeException("Failed to parse plant analysis response: " + e.getMessage(), e);
        }
    }

    /**
     * Perform pruning analysis with 1–3 images.
     */
    public PruningAnalysisSchema analyzePruning(
            List<String> imagesBase64, List<String> mimeTypes,
            String genus, String species,
            String goalsText, String pruningGuidance,
            Long plantId, Long analysisId) {

        String promptKey = "pruning_analysis_v1";
        String systemPrompt = resolvePrompt(promptKey, "system");
        String userPrompt = renderUserPrompt(promptKey,
                Map.of("genus", Optional.ofNullable(genus).orElse("Unknown"),
                        "species", Optional.ofNullable(species).orElse("Unknown"),
                        "image_count", String.valueOf(imagesBase64.size()),
                        "goals_text", Optional.ofNullable(goalsText).orElse(""),
                        "pruning_guidance", Optional.ofNullable(pruningGuidance).orElse("")));

        Map<String, Object> inputVariables = new HashMap<>();
        inputVariables.put("genus", genus);
        inputVariables.put("species", species);
        inputVariables.put("goals_text", goalsText);
        inputVariables.put("image_count", imagesBase64.size());

        String responseText = callWithImages(systemPrompt, userPrompt, imagesBase64, mimeTypes,
                promptKey, inputVariables, plantId, analysisId, pruningSchema());

        try {
            JsonNode root = objectMapper.readTree(responseText);
            String outputText = extractOutputText(root);
            return objectMapper.readValue(outputText, PruningAnalysisSchema.class);
        } catch (Exception e) {
            log.error("Failed to parse pruning analysis response", e);
            throw new RuntimeException("Failed to parse pruning response: " + e.getMessage(), e);
        }
    }

    private String callWithImage(
            String systemPrompt, String userPrompt,
            String imageBase64, String mimeType,
            String promptKey, Map<String, Object> inputVariables,
            Long plantId, Long analysisId,
            Map<String, Object> schema) {

        return callWithImages(systemPrompt, userPrompt,
                List.of(imageBase64), List.of(mimeType),
                promptKey, inputVariables, plantId, analysisId, schema);
    }

    private String callWithImages(
            String systemPrompt, String userPrompt,
            List<String> imagesBase64, List<String> mimeTypes,
            String promptKey, Map<String, Object> inputVariables,
            Long plantId, Long analysisId,
            Map<String, Object> schema) {

        List<Map<String, Object>> userContent = new ArrayList<>();
        userContent.add(Map.of("type", "input_text", "text", userPrompt));

        for (int i = 0; i < imagesBase64.size(); i++) {
            userContent.add(Map.of(
                    "type", "input_image",
                    "image_url", "data:" + mimeTypes.get(i) + ";base64," + imagesBase64.get(i)
            ));
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)
                ),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", promptKey.replace("_v1", "_response"),
                                "schema", schema,
                                "strict", true
                        )
                )
        );

        String renderedPrompt = systemPrompt + "\n\n" + userPrompt;
        String responseText;

        try {
            responseText = webClientBuilder.build()
                    .post()
                    .uri(OPENAI_API_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("OpenAI API call failed for promptKey={}", promptKey, e);
            throw new RuntimeException("OpenAI API call failed: " + e.getMessage(), e);
        }

        // Audit log the request per Database-Prompt-Storage spec
        LlmRequest auditRecord = LlmRequest.builder()
                .promptKey(promptKey)
                .model(model)
                .renderedPrompt(renderedPrompt)
                .inputVariables(new HashMap<>(inputVariables))
                .responseText(responseText)
                .plantId(plantId)
                .analysisId(analysisId)
                .build();
        requestRepository.save(auditRecord);

        return responseText;
    }

    private String resolvePrompt(String promptKey, String role) {
        return promptRepository
                .findFirstByPromptKeyAndRoleAndIsActiveTrueOrderByVersionDesc(promptKey, role)
                .map(LlmPrompt::getContent)
                .orElseThrow(() -> new IllegalStateException(
                        "No active prompt found for key=" + promptKey + " role=" + role));
    }

    private String renderUserPrompt(String promptKey, Map<String, Object> variables) {
        String template = promptRepository
                .findFirstByPromptKeyAndRoleAndIsActiveTrueOrderByVersionDesc(promptKey, "user")
                .map(LlmPrompt::getContent)
                .orElseThrow(() -> new IllegalStateException("No user prompt for " + promptKey));

        // Simple Handlebars-style variable substitution
        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
            // Handle {{#if key}}...{{/if}} blocks
            if (!value.isEmpty()) {
                rendered = rendered.replaceAll("\\{\\{#if " + entry.getKey() + "\\}\\}(.*?)\\{\\{/if\\}\\}", "$1");
            } else {
                rendered = rendered.replaceAll("\\{\\{#if " + entry.getKey() + "\\}\\}.*?\\{\\{/if\\}\\}", "");
            }
        }
        return rendered.trim();
    }

    private String extractOutputText(JsonNode root) {
        // OpenAI Responses API: output[0].content[0].text
        JsonNode output = root.path("output");
        if (output.isArray() && !output.isEmpty()) {
            JsonNode content = output.get(0).path("content");
            if (content.isArray() && !content.isEmpty()) {
                return content.get(0).path("text").asText();
            }
        }
        throw new RuntimeException("Unexpected OpenAI response structure: " + root);
    }

    private Map<String, Object> registrationSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("className", Map.of("type", "string"));
        properties.put("genus", Map.of("type", "string"));
        properties.put("species", Map.of("type", "string"));
        properties.put("variety", Map.of("type", "string"));
        properties.put("scientificName", Map.of("type", "string"));
        properties.put("confidence", Map.of("type", "string"));
        properties.put("nativeRegions", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("lightNeeds", Map.of("type", "string"));
        properties.put("placementGuidance", Map.of("type", "string"));
        properties.put("wateringAmount", Map.of("type", "string"));
        properties.put("wateringFrequency", Map.of("type", "string"));
        properties.put("wateringGuidance", Map.of("type", "string"));
        properties.put("fertilizerType", Map.of("type", "string"));
        properties.put("fertilizerFrequency", Map.of("type", "string"));
        properties.put("fertilizerGuidance", Map.of("type", "string"));
        properties.put("pruningGuidance", Map.of("type", "string"));
        properties.put("propagationInstructions", Map.of("type", "string"));
        properties.put("healthDiagnosis", Map.of("type", "string"));
        properties.put("goalSuggestions", Map.of("type", "string"));
        properties.put("interestingFacts", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("uses", Map.of("type", "array", "items", Map.of("type", "string")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("genus", "species", "confidence"));
        return schema;
    }

    private Map<String, Object> pruningSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "pruningNeeded", Map.of("type", "boolean"),
                        "verdict", Map.of("type", "string"),
                        "pruningAmount", Map.of("type", "string"),
                        "specificRecommendations", Map.of("type", "array", "items", Map.of("type", "string")),
                        "goalAlignment", Map.of("type", "string"),
                        "confidence", Map.of("type", "string"),
                        "notes", Map.of("type", "string")
                ),
                "required", List.of("pruningNeeded", "verdict", "confidence")
        );
    }
}
