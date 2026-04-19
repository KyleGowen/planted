package com.planted.bio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-section OpenAI structured-output schemas. Kept together so they can be
 * audited at a glance and so strategies stay focused on input assembly.
 */
public final class BioSectionSchemas {

    private BioSectionSchemas() {}

    public static Map<String, Object> speciesId() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("className", Map.of("type", "string"));
        props.put("taxonomicFamily", Map.of("type", "string"));
        props.put("genus", Map.of("type", "string"));
        props.put("species", Map.of(
                "type", "string",
                "description",
                "ONLY the specific epithet—the second part of the binomial (e.g. trifasciata). Empty string if unknown."));
        props.put("variety", Map.of("type", "string"));
        props.put("confidence", Map.of("type", "string"));
        props.put("nativeRegions", Map.of("type", "array", "items", Map.of("type", "string")));
        return object(props, List.of("className", "taxonomicFamily", "genus", "species", "variety", "confidence", "nativeRegions"));
    }

    public static Map<String, Object> healthAssessment() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("diagnosis", Map.of(
                "type", "string",
                "description", "2–5 sentences of realistic assessment per system prompt."));
        props.put("severity", Map.of(
                "type", "string",
                "enum", List.of("NONE", "MILD", "MODERATE", "SEVERE", "UNCERTAIN")));
        props.put("signs", Map.of("type", "array", "items", Map.of("type", "string")));
        props.put("checks", Map.of("type", "array", "items", Map.of("type", "string")));
        props.put("attentionNeeded", Map.of(
                "type", "boolean",
                "description",
                "True when the plant visibly needs owner attention for its health (severity MILD/MODERATE/SEVERE). False for NONE/UNCERTAIN."));
        props.put("attentionReason", Map.of(
                "type", "string",
                "description",
                "Short tooltip-length phrase (under 80 chars) summarising the health concern. Empty string when attentionNeeded is false."));
        return object(props,
                List.of("diagnosis", "severity", "signs", "checks", "attentionNeeded", "attentionReason"));
    }

    public static Map<String, Object> speciesDescription() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("overview", Map.of(
                "type", "string",
                "description", "1–3 paragraphs of neutral encyclopedia prose, separated by two newline characters."));
        props.put("uses", Map.of("type", "array", "items", Map.of("type", "string")));
        return object(props, List.of("overview", "uses"));
    }

    public static Map<String, Object> waterCare() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("amount", Map.of("type", "string"));
        props.put("frequency", Map.of("type", "string"));
        props.put("guidance", Map.of("type", "string"));
        return object(props, List.of("amount", "frequency", "guidance"));
    }

    public static Map<String, Object> fertilizerCare() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("type", Map.of("type", "string"));
        props.put("frequency", Map.of("type", "string"));
        props.put("guidance", Map.of("type", "string"));
        return object(props, List.of("type", "frequency", "guidance"));
    }

    public static Map<String, Object> pruningCare() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("actionSummary", Map.of("type", "string"));
        props.put("guidance", Map.of("type", "string"));
        props.put("generalGuidance", Map.of("type", "string"));
        return object(props, List.of("actionSummary", "guidance", "generalGuidance"));
    }

    public static Map<String, Object> lightCare() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("needs", Map.of("type", "string"));
        props.put("generalGuidance", Map.of("type", "string"));
        props.put("attentionNeeded", Map.of(
                "type", "boolean",
                "description",
                "True when the plant's current placement/location likely gives it too much or too little light. False when light is a good match or unknown."));
        props.put("attentionReason", Map.of(
                "type", "string",
                "description",
                "Short tooltip-length phrase (under 80 chars) such as \"Likely too dark for this species\" or \"Harsh afternoon sun may scorch leaves\". Empty string when attentionNeeded is false."));
        return object(props,
                List.of("needs", "generalGuidance", "attentionNeeded", "attentionReason"));
    }

    public static Map<String, Object> placementCare() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("guidance", Map.of("type", "string"));
        props.put("generalGuidance", Map.of("type", "string"));
        props.put("attentionNeeded", Map.of(
                "type", "boolean",
                "description",
                "True when the plant should be moved or repotted (e.g. pot outgrown, drafty spot, unsuitable environment for the season). False when the current placement is appropriate or unknown."));
        props.put("attentionReason", Map.of(
                "type", "string",
                "description",
                "Short tooltip-length phrase (under 80 chars) such as \"Likely outgrowing its pot\" or \"Drafty spot near a vent\". Empty string when attentionNeeded is false."));
        return object(props,
                List.of("guidance", "generalGuidance", "attentionNeeded", "attentionReason"));
    }

    public static Map<String, Object> historySummary() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("summary", Map.of(
                "type", "string",
                "description", "One short paragraph (3–6 sentences) describing the plant's history. Empty string if timeline is empty."));
        return object(props, List.of("summary"));
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }
}
