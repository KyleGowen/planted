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

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    /**
     * Perform plant registration / reanalysis.
     *
     * @param imageBase64      base64-encoded image data
     * @param mimeType         image mime type
     * @param goalsText        optional user goals
     * @param location         optional indoor placement description (e.g. "living room window")
     * @param plantName        optional user-given name for the plant
     * @param priorCareContext optional formatted care context from a prior analysis (used on reanalysis)
     * @param careHistory      optional formatted care event history (last watered, fertilized, pruned)
     * @param historyNotes     optional formatted owner observations from plant history entries
     * @param geoCountry       optional geographic country for climate-aware care recommendations
     * @param geoState         optional geographic state/region for climate-aware care recommendations
     * @param geoCity          optional geographic city for climate-aware care recommendations
     * @param plantId          for audit logging
     * @param analysisId       for audit logging
     */
    public PlantAnalysisSchema analyzeRegistration(
            String imageBase64, String mimeType,
            String goalsText, String location,
            String plantName, String priorCareContext, String careHistory, String historyNotes,
            String geoCountry, String geoState, String geoCity,
            Long plantId, Long analysisId) {

        String promptKey = "plant_registration_analysis_v1";
        String systemPrompt = resolvePrompt(promptKey, "system");

        String geographicLocation = buildGeographicLocation(geoCountry, geoState, geoCity);

        Map<String, String> templateVars = new HashMap<>();
        templateVars.put("goals_text", Optional.ofNullable(goalsText).orElse(""));
        templateVars.put("location", Optional.ofNullable(location).orElse(""));
        templateVars.put("plant_name", Optional.ofNullable(plantName).orElse(""));
        templateVars.put("prior_care_context", Optional.ofNullable(priorCareContext).orElse(""));
        templateVars.put("care_history", Optional.ofNullable(careHistory).orElse(""));
        templateVars.put("history_notes", Optional.ofNullable(historyNotes).orElse(""));
        templateVars.put("geographic_location", Optional.ofNullable(geographicLocation).orElse(""));
        String userPrompt = renderUserPrompt(promptKey, templateVars);

        Map<String, Object> inputVariables = new HashMap<>();
        inputVariables.put("goals_text", goalsText);
        inputVariables.put("location", location);
        inputVariables.put("plant_name", plantName);
        inputVariables.put("prior_care_context", priorCareContext);
        inputVariables.put("care_history", careHistory);
        inputVariables.put("history_notes", historyNotes);
        inputVariables.put("geographic_location", geographicLocation);

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
     *
     * @param careHistory  optional formatted care event history (last watered, fertilized, pruned)
     * @param historyNotes optional formatted owner observations from plant history entries
     */
    public PruningAnalysisSchema analyzePruning(
            List<String> imagesBase64, List<String> mimeTypes,
            String genus, String species,
            String goalsText, String pruningGuidance,
            String careHistory, String historyNotes,
            Long plantId, Long analysisId) {

        String promptKey = "pruning_analysis_v1";
        String systemPrompt = resolvePrompt(promptKey, "system");

        Map<String, String> templateVars = new HashMap<>();
        templateVars.put("genus", Optional.ofNullable(genus).orElse("Unknown"));
        templateVars.put("species", Optional.ofNullable(species).orElse("Unknown"));
        templateVars.put("image_count", String.valueOf(imagesBase64.size()));
        templateVars.put("goals_text", Optional.ofNullable(goalsText).orElse(""));
        templateVars.put("pruning_guidance", Optional.ofNullable(pruningGuidance).orElse(""));
        templateVars.put("care_history", Optional.ofNullable(careHistory).orElse(""));
        templateVars.put("history_notes", Optional.ofNullable(historyNotes).orElse(""));
        String userPrompt = renderUserPrompt(promptKey, templateVars);

        Map<String, Object> inputVariables = new HashMap<>();
        inputVariables.put("genus", genus);
        inputVariables.put("species", species);
        inputVariables.put("goals_text", goalsText);
        inputVariables.put("image_count", imagesBase64.size());
        inputVariables.put("care_history", careHistory);
        inputVariables.put("history_notes", historyNotes);

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

        // Build user message content: text + images
        List<Map<String, Object>> userContent = new ArrayList<>();
        userContent.add(Map.of("type", "text", "text", userPrompt));
        for (int i = 0; i < imagesBase64.size(); i++) {
            userContent.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of(
                            "url", "data:" + mimeTypes.get(i) + ";base64," + imagesBase64.get(i),
                            "detail", "auto"
                    )
            ));
        }

        // Chat Completions API with structured outputs (response_format)
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)
                ),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", promptKey.replace("_v1", "_response"),
                                "schema", schema,
                                "strict", true
                        )
                )
        );

        String renderedPrompt = systemPrompt + "\n\n" + userPrompt;
        String responseText = callWithRetry(requestBody, promptKey);

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

    private String renderUserPrompt(String promptKey, Map<String, String> variables) {
        String template = promptRepository
                .findFirstByPromptKeyAndRoleAndIsActiveTrueOrderByVersionDesc(promptKey, "user")
                .map(LlmPrompt::getContent)
                .orElseThrow(() -> new IllegalStateException("No user prompt for " + promptKey));

        // Handlebars-style substitution; (?s) enables DOTALL so {{#if}} blocks can span multiple lines
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
            if (!value.isEmpty()) {
                rendered = rendered.replaceAll("(?s)\\{\\{#if " + entry.getKey() + "\\}\\}(.*?)\\{\\{/if\\}\\}", "$1");
            } else {
                rendered = rendered.replaceAll("(?s)\\{\\{#if " + entry.getKey() + "\\}\\}.*?\\{\\{/if\\}\\}", "");
            }
        }
        return rendered.trim();
    }

    /** Calls OpenAI with up to 3 retries on 429 (rate limit), backing off 5s between attempts. */
    private String callWithRetry(Map<String, Object> requestBody, String promptKey) {
        int maxAttempts = 3;
        long backoffMs = 5_000;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return webClientBuilder.build()
                        .post()
                        .uri(OPENAI_API_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                lastException = e;
                if (e.getStatusCode().value() == 429 && attempt < maxAttempts) {
                    log.warn("OpenAI rate limited (attempt {}/{}), retrying in {}ms — promptKey={}",
                            attempt, maxAttempts, backoffMs, promptKey);
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    backoffMs *= 2; // exponential backoff
                } else {
                    log.error("OpenAI API call failed for promptKey={}: {} — body: {}",
                            promptKey, e.getMessage(), e.getResponseBodyAsString());
                    throw new RuntimeException("OpenAI API call failed: " + e.getMessage(), e);
                }
            }
        }
        throw new RuntimeException("OpenAI API call failed after retries", lastException);
    }

    private String extractOutputText(JsonNode root) {
        // Chat Completions API: choices[0].message.content
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            String content = choices.get(0).path("message").path("content").asText(null);
            if (content != null && !content.isEmpty()) {
                return content;
            }
        }
        throw new RuntimeException("Unexpected OpenAI response structure: " + root);
    }

    /**
     * Combines geo fields into a single location string for the prompt (e.g. "Austin, Texas, USA").
     * Returns null when no geo fields are set.
     */
    private String buildGeographicLocation(String geoCountry, String geoState, String geoCity) {
        List<String> parts = new ArrayList<>();
        if (geoCity != null && !geoCity.isBlank()) parts.add(geoCity.trim());
        if (geoState != null && !geoState.isBlank()) parts.add(geoState.trim());
        if (geoCountry != null && !geoCountry.isBlank()) parts.add(geoCountry.trim());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private Map<String, Object> registrationSchema() {
        // strict: true requires every property to be in "required" and additionalProperties: false
        List<String> allFields = List.of(
                "className", "genus", "species", "variety", "scientificName", "confidence",
                "nativeRegions", "lightNeeds", "placementGuidance",
                "wateringAmount", "wateringFrequency", "wateringGuidance",
                "fertilizerType", "fertilizerFrequency", "fertilizerGuidance",
                "pruningGuidance", "propagationInstructions",
                "healthDiagnosis", "goalSuggestions",
                "interestingFacts", "uses"
        );

        Map<String, Object> properties = new LinkedHashMap<>();
        for (String field : allFields) {
            if (field.equals("nativeRegions") || field.equals("interestingFacts") || field.equals("uses")) {
                properties.put(field, Map.of("type", "array", "items", Map.of("type", "string")));
            } else {
                properties.put(field, Map.of("type", "string"));
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", allFields);
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> pruningSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("pruningNeeded", Map.of("type", "boolean"));
        properties.put("verdict", Map.of("type", "string"));
        properties.put("pruningAmount", Map.of("type", "string"));
        properties.put("specificRecommendations", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("goalAlignment", Map.of("type", "string"));
        properties.put("confidence", Map.of("type", "string"));
        properties.put("notes", Map.of("type", "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("pruningNeeded", "verdict", "pruningAmount",
                "specificRecommendations", "goalAlignment", "confidence", "notes"));
        schema.put("additionalProperties", false);
        return schema;
    }
}
