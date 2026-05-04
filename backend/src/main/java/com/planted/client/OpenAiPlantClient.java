package com.planted.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planted.entity.LlmPrompt;
import com.planted.entity.LlmRequest;
import com.planted.repository.LlmPromptRepository;
import com.planted.repository.LlmRequestRepository;
import com.planted.service.UserSettingsService;
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
    private final UserSettingsService userSettingsService;

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
     * @param geoCity               optional geographic city for climate-aware care recommendations
     * @param ownerPhysicalAddress optional owner's home / growing-site address for regional climate context
     * @param plantId               for audit logging
     * @param analysisId            for audit logging
     */
    public PlantAnalysisSchema analyzeRegistration(
            String imageBase64, String mimeType,
            String goalsText, String location,
            String plantName, String priorCareContext, String careHistory, String historyNotes,
            String geoCountry, String geoState, String geoCity,
            String ownerPhysicalAddress,
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
        templateVars.put("owner_physical_address", Optional.ofNullable(ownerPhysicalAddress).orElse(""));
        String userPrompt = renderUserPrompt(promptKey, templateVars);

        Map<String, Object> inputVariables = new HashMap<>();
        inputVariables.put("goals_text", goalsText);
        inputVariables.put("location", location);
        inputVariables.put("plant_name", plantName);
        inputVariables.put("prior_care_context", priorCareContext);
        inputVariables.put("care_history", careHistory);
        inputVariables.put("history_notes", historyNotes);
        inputVariables.put("geographic_location", geographicLocation);
        inputVariables.put("owner_physical_address", ownerPhysicalAddress);

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
            String ownerPhysicalAddress,
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
        templateVars.put("owner_physical_address", Optional.ofNullable(ownerPhysicalAddress).orElse(""));
        String userPrompt = renderUserPrompt(promptKey, templateVars);

        Map<String, Object> inputVariables = new HashMap<>();
        inputVariables.put("genus", genus);
        inputVariables.put("species", species);
        inputVariables.put("goals_text", goalsText);
        inputVariables.put("image_count", imagesBase64.size());
        inputVariables.put("pruning_guidance", pruningGuidance);
        inputVariables.put("care_history", careHistory);
        inputVariables.put("history_notes", historyNotes);
        inputVariables.put("owner_physical_address", ownerPhysicalAddress);

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

    /**
     * Summarize owner journal + care timeline into narrative text for the About pane.
     *
     * @param plantProfile   placement, goals, geo, and latest care-analysis snapshot (may be blank)
     * @param baselinePhotoNote optional note when baseline image could not be attached (e.g. S3)
     * @param imagesBase64          vision images in prompt order: baseline first (if any), then journal photos
     * @param ownerPhysicalAddress optional owner's address for typical climate context only
     */
    public PlantHistorySummarySchema summarizePlantHistory(
            String plantProfile,
            String baselinePhotoNote,
            String timelineText,
            String plantName,
            String speciesLabel,
            String imageCountLabel,
            String ownerPhysicalAddress,
            List<String> imagesBase64,
            List<String> mimeTypes,
            Long plantId,
            Long analysisId) {

        String apiKey = userSettingsService.getEffectiveOpenAiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI is not configured: set planted.openai.api-key (or OPENAI_API_KEY) to generate a history summary.");
        }

        String promptKey = "plant_history_summary_v2";
        String systemPrompt = resolvePrompt(promptKey, "system");

        Map<String, String> templateVars = new HashMap<>();
        templateVars.put("plant_profile", Optional.ofNullable(plantProfile).orElse(""));
        templateVars.put("baseline_photo_note", Optional.ofNullable(baselinePhotoNote).orElse(""));
        templateVars.put("plant_name", Optional.ofNullable(plantName).orElse(""));
        templateVars.put("species_label", Optional.ofNullable(speciesLabel).orElse(""));
        templateVars.put("timeline_text", timelineText != null ? timelineText : "");
        templateVars.put("image_count", Optional.ofNullable(imageCountLabel).orElse(""));
        templateVars.put("owner_physical_address", Optional.ofNullable(ownerPhysicalAddress).orElse(""));
        String userPrompt = renderUserPrompt(promptKey, templateVars);

        Map<String, Object> inputVariables = new HashMap<>();
        inputVariables.put("plant_profile", plantProfile);
        inputVariables.put("baseline_photo_note", baselinePhotoNote);
        inputVariables.put("plant_name", plantName);
        inputVariables.put("species_label", speciesLabel);
        inputVariables.put("timeline_text", timelineText);
        inputVariables.put("image_count", imageCountLabel);
        inputVariables.put("owner_physical_address", ownerPhysicalAddress);

        List<String> safeImages = imagesBase64 != null ? imagesBase64 : List.of();
        List<String> safeMimes = mimeTypes != null ? mimeTypes : List.of();

        String responseText = callWithImages(systemPrompt, userPrompt,
                safeImages, safeMimes,
                promptKey, inputVariables, plantId, analysisId, historySummarySchema());

        try {
            JsonNode root = objectMapper.readTree(responseText);
            String outputText = extractOutputText(root);
            PlantHistorySummarySchema parsed = objectMapper.readValue(outputText, PlantHistorySummarySchema.class);
            if (parsed.getDailyDigests() == null || parsed.getDailyDigests().isEmpty()) {
                throw new IllegalStateException("Model returned no daily_digests.");
            }
            String flat = parsed.flattenForStorage();
            if (flat.isBlank()) {
                throw new IllegalStateException("Model returned empty digest text.");
            }
            return parsed;
        } catch (Exception e) {
            log.error("Failed to parse plant history summary response", e);
            throw new RuntimeException("Failed to parse plant history summary: " + e.getMessage(), e);
        }
    }

    /**
     * Compresses an owner's free-text placement notes into a single-sentence caption used
     * as the third line under Indoor/Outdoor in the plant detail header. Dedicated,
     * text-only LLM call (no image) with its own small JSON schema — independent of
     * {@link #analyzeRegistration}. Returns an empty string when the notes are missing
     * or unusable per the prompt contract.
     *
     * @param location the owner's raw placement notes (may be null/blank)
     * @param plantId  for audit logging
     */
    public String summarizePlacementNotes(String location, Long plantId) {
        String apiKey = userSettingsService.getEffectiveOpenAiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI is not configured: set planted.openai.api-key (or OPENAI_API_KEY) to summarize placement notes.");
        }

        String promptKey = "plant_placement_notes_summary_v1";
        String systemPrompt = resolvePrompt(promptKey, "system");

        Map<String, String> templateVars = new HashMap<>();
        templateVars.put("location", Optional.ofNullable(location).orElse(""));
        String userPrompt = renderUserPrompt(promptKey, templateVars);

        Map<String, Object> inputVariables = new HashMap<>();
        inputVariables.put("location", location);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", responseFormatSchemaName(promptKey),
                                "schema", placementNotesSummarySchema(),
                                "strict", true
                        )
                )
        );

        String renderedPrompt = systemPrompt + "\n\n" + userPrompt;
        String responseText = callWithRetry(requestBody, promptKey);

        LlmRequest auditRecord = LlmRequest.builder()
                .promptKey(promptKey)
                .model(model)
                .renderedPrompt(renderedPrompt)
                .inputVariables(new HashMap<>(inputVariables))
                .responseText(responseText)
                .plantId(plantId)
                .build();
        requestRepository.save(auditRecord);

        try {
            JsonNode root = objectMapper.readTree(responseText);
            String outputText = extractOutputText(root);
            JsonNode parsed = objectMapper.readTree(outputText);
            String summary = parsed.path("summary").asText("");
            return summary == null ? "" : summary.trim();
        } catch (Exception e) {
            log.error("Failed to parse placement notes summary response", e);
            throw new RuntimeException("Failed to parse placement notes summary: " + e.getMessage(), e);
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

    /**
     * Generic entry point for decomposed bio-section calls. Resolves the given
     * prompt key from {@code llm_prompts}, renders user variables, and calls
     * OpenAI with the supplied JSON schema. When {@code imagesBase64} is empty
     * this is a text-only call; otherwise it is a vision call. Returns the
     * parsed JSON output of the model.
     */
    public JsonNode generateBioSection(
            String promptKey,
            Map<String, String> templateVars,
            Map<String, Object> schema,
            List<String> imagesBase64,
            List<String> mimeTypes,
            Long plantId) {

        String apiKey = userSettingsService.getEffectiveOpenAiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI is not configured: set planted.openai.api-key (or OPENAI_API_KEY) to generate bio sections.");
        }

        String systemPrompt = resolvePrompt(promptKey, "system");
        String userPrompt = renderUserPrompt(promptKey, templateVars == null ? Map.of() : templateVars);

        Map<String, Object> inputVariables = new HashMap<>();
        if (templateVars != null) inputVariables.putAll(templateVars);

        String responseText;
        List<String> imgs = imagesBase64 != null ? imagesBase64 : List.of();
        List<String> mimes = mimeTypes != null ? mimeTypes : List.of();

        if (imgs.isEmpty()) {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "response_format", Map.of(
                            "type", "json_schema",
                            "json_schema", Map.of(
                                    "name", responseFormatSchemaName(promptKey),
                                    "schema", schema,
                                    "strict", true
                            )
                    )
            );
            String renderedPrompt = systemPrompt + "\n\n" + userPrompt;
            responseText = callWithRetry(requestBody, promptKey);
            LlmRequest auditRecord = LlmRequest.builder()
                    .promptKey(promptKey)
                    .model(model)
                    .renderedPrompt(renderedPrompt)
                    .inputVariables(new HashMap<>(inputVariables))
                    .responseText(responseText)
                    .plantId(plantId)
                    .build();
            requestRepository.save(auditRecord);
        } else {
            responseText = callWithImages(
                    systemPrompt, userPrompt,
                    imgs, mimes,
                    promptKey, inputVariables, plantId, null, schema);
        }

        try {
            JsonNode root = objectMapper.readTree(responseText);
            String outputText = extractOutputText(root);
            return objectMapper.readTree(outputText);
        } catch (Exception e) {
            log.error("Failed to parse bio section response for promptKey={}", promptKey, e);
            throw new RuntimeException("Failed to parse bio section response: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the active version number for the given {@code promptKey} / {@code role}. Used to
     * stamp bio-section cache rows so we can detect and refresh when prompts are rev'd.
     */
    public Integer resolveActivePromptVersion(String promptKey) {
        return promptRepository
                .findFirstByPromptKeyAndRoleAndIsActiveTrueOrderByVersionDesc(promptKey, "system")
                .map(LlmPrompt::getVersion)
                .orElse(null);
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
                                "name", responseFormatSchemaName(promptKey),
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
        String apiKey = userSettingsService.getEffectiveOpenAiApiKey();
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

    /** OpenAI json_schema name derived from prompt key (e.g. plant_history_summary_v2 → plant_history_summary_response). */
    private static String responseFormatSchemaName(String promptKey) {
        return promptKey.replaceFirst("_v\\d+$", "_response");
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
                "className", "taxonomicFamily", "genus", "species", "variety", "confidence",
                "nativeRegions", "lightNeeds", "lightGeneralGuidance",
                "placementGuidance", "placementGeneralGuidance",
                "wateringAmount", "wateringFrequency", "wateringGuidance",
                "fertilizerType", "fertilizerFrequency", "fertilizerGuidance",
                "pruningActionSummary", "pruningGeneralGuidance",
                "propagationInstructions",
                "healthDiagnosis", "goalSuggestions",
                "speciesOverview", "uses"
        );

        Map<String, Object> properties = new LinkedHashMap<>();
        for (String field : allFields) {
            if (field.equals("nativeRegions") || field.equals("uses")) {
                properties.put(field, Map.of("type", "array", "items", Map.of("type", "string")));
            } else if (field.equals("speciesOverview")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Exactly 1-3 paragraphs of neutral, scientific encyclopedia prose like a Wikipedia "
                                + "article lead: each paragraph multiple full sentences; separate paragraphs with "
                                + "two newline characters; third person only; no bullets or numbered lists; cover "
                                + "taxonomy, morphology, native range/ecology at high level without duplicating "
                                + "nativeRegions verbatim, horticultural role, brief indoor context without "
                                + "repeating structured care schedules; conservative pest/disease mentions; "
                                + "state uncertainty clearly if identification is weak."
                ));
            } else if (field.equals("taxonomicFamily")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Botanical family when confidently known from the identification "
                                + "(e.g. Asparagaceae). Empty string if unknown or uncertain. Do not prefix with "
                                + "the word 'Family'."
                ));
            } else if (field.equals("genus")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Latin genus when known (e.g. Dracaena). Empty string if not narrowed beyond a higher rank."
                ));
            } else if (field.equals("species")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "ONLY the specific epithet—the second part of the binomial (e.g. trifasciata)—"
                                + "not the full binomial. Lowercase for Latin epithets. Empty string if unknown."
                ));
            } else if (field.equals("variety")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Botanical variety, subspecies, or unambiguous cultivar/group when supported by evidence; "
                                + "otherwise empty string."
                ));
            } else if (field.equals("lightNeeds")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Short primary line for the UI: brightness level for this plant in its current context "
                                + "(e.g. bright indirect). One phrase or one short sentence only; not a paragraph."
                ));
            } else if (field.equals("lightGeneralGuidance")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Secondary educational text for the UI: what the light label means, typical distance from "
                                + "windows, seasonal changes, conservative signs of too much or too little light. "
                                + "Do not repeat the exact wording of lightNeeds."
                ));
            } else if (field.equals("placementGuidance")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Short primary line tailored to the user's stated location and/or geography when known; "
                                + "specific placement recommendation for this instance. One or two short sentences."
                ));
            } else if (field.equals("placementGeneralGuidance")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Secondary educational text: general room environment tips for this species (drafts, "
                                + "humidity, grouping with other plants, rotation for even growth if appropriate). "
                                + "Do not repeat the exact wording of placementGuidance."
                ));
            } else if (field.equals("pruningActionSummary")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Primary UI line for this individual plant in the image: two or three short sentences that "
                                + "MUST include (1) WHEN to prune—best season or month window, growth phase, or "
                                + "visible triggers (leggy, crowded, dead wood), using geographic season and care "
                                + "history last-pruned date when provided; if unsure, say what to wait for before "
                                + "cutting. (2) HOW MUCH to remove—conservative degree (dead/damaged only, light "
                                + "tips, thin a few stems, or cap live foliage removal around one-quarter to "
                                + "one-third per session unless clearly justified). Valid to state no pruning "
                                + "needed now. Do not move species background here; keep that in "
                                + "pruningGeneralGuidance."
                ));
            } else if (field.equals("pruningGeneralGuidance")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Secondary educational text: species-typical pruning habits and rationale, general "
                                + "seasonality—do not repeat the same specific when/how-much plan from "
                                + "pruningActionSummary. 'No pruning required' or minimal pruning is a valid theme."
                ));
            } else if (field.equals("healthDiagnosis")) {
                properties.put(field, Map.of(
                        "type", "string",
                        "description",
                        "Realistic assessment of THIS plant's visible condition. Do NOT default to 'healthy'. "
                                + "Actively scan the image for chlorosis, necrosis, crispy edges, wilting, leaf "
                                + "drop, curling, leggy/etiolated growth, pale color, sunburn, mechanical damage, "
                                + "pests or webbing, powdery or sooty coatings, mold, soil crust, rot at base, and "
                                + "pot/substrate issues. When signs are present, describe them concretely—which "
                                + "leaves, which part of the leaf, and the pattern—in priority order. For each "
                                + "finding you MUST explicitly consider and name whichever of these three "
                                + "dominant houseplant stressors fit: (1) LIGHT LEVELS—too little (pale color, "
                                + "leggy stretching, small new growth) vs too much or wrong quality (bleached "
                                + "patches, sunburn, faded color on sun-side leaves); (2) WATERING, with "
                                + "particular attention to OVERWATERING (uniformly yellow lower leaves, soft or "
                                + "mushy stems, persistently wet or sour soil, fungus gnats, wilting despite wet "
                                + "soil, black stem/crown rot) distinguished from underwatering cues when "
                                + "visible; (3) PLACEMENT (heat vents, AC, cold drafts, leaf contact with cold "
                                + "glass, low humidity, no airflow, saucer with standing water, pot too small "
                                + "or too large, hidden behind a curtain). Offer 1–3 plausible causes per "
                                + "finding and do not lock to a single cause; do not assume watering alone. "
                                + "Close with 1–3 short owner-checkable prompts that target whichever of light, "
                                + "watering, or placement is most plausible (e.g. 'Is the soil still wet 2 in "
                                + "down?', 'How many hours of bright light does this spot get?', 'Is it near a "
                                + "heat vent or cold window?'). Only call the plant healthy when there are "
                                + "genuinely no visible concerns AND the image is clear enough to judge; when "
                                + "the photo is blurry, cropped, or poorly lit, say the image is insufficient "
                                + "for a confident health read rather than declaring health. Tone: honest and "
                                + "specific, not alarmist; beginner-friendly. Do not turn this into a full care "
                                + "plan — schedules belong in the dedicated watering / light / placement fields."
                ));
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

    private Map<String, Object> placementNotesSummarySchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", Map.of(
                "type", "string",
                "description",
                "Single-sentence paraphrase of the owner's placement notes (<= ~120 characters). "
                        + "Empty string when notes are missing or unusable."
        ));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("summary"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> historySummarySchema() {
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("day", Map.of(
                "type", "string",
                "description", "Local calendar day in ISO format YYYY-MM-DD."));
        itemProps.put("digest", Map.of(
                "type", "string",
                "description", "Short narrative for that day; include accurate care counts when provided in the log."));
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("properties", itemProps);
        itemSchema.put("required", List.of("day", "digest"));
        itemSchema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("daily_digests", Map.of(
                "type", "array",
                "description", "One entry per calendar day with activity, newest day first.",
                "items", itemSchema));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("daily_digests"));
        schema.put("additionalProperties", false);
        return schema;
    }
}
