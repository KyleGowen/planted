package com.planted.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Structured output schema for pruning analysis responses from OpenAI.
 * Conservative answers ("No pruning required") are explicitly supported.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PruningAnalysisSchema {

    /** Whether pruning is needed. False is a valid, preferred answer when evidence is weak. */
    private boolean pruningNeeded;

    /** Summary verdict, e.g. "No pruning required" or "Light pruning recommended" */
    private String verdict;

    private String pruningAmount;
    private List<String> specificRecommendations;
    private String goalAlignment;
    private String confidence;
    private String notes;
}
