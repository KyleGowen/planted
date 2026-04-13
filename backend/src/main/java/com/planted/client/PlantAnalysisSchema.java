package com.planted.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Structured output schema for plant registration / reanalysis responses from OpenAI.
 * Maps directly to the JSON schema sent in the structured_output spec.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlantAnalysisSchema {

    private String className;
    private String genus;
    private String species;
    private String variety;
    private String scientificName;
    private String confidence;

    private List<String> nativeRegions;
    private String lightNeeds;
    private String placementGuidance;
    private String wateringAmount;
    private String wateringFrequency;
    private String wateringGuidance;
    private String fertilizerType;
    private String fertilizerFrequency;
    private String fertilizerGuidance;
    private String pruningGuidance;
    private String propagationInstructions;
    private String healthDiagnosis;
    private String goalSuggestions;
    private List<String> interestingFacts;
    private List<String> uses;
}
