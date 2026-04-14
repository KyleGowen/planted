package com.planted.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AnalysisSummaryDto(
        Long id,
        String analysisType,
        String status,
        String genus,
        String species,
        String variety,
        String scientificName,
        String confidence,
        List<String> nativeRegions,
        String lightNeeds,
        String placementGuidance,
        String wateringGuidance,
        String wateringAmount,
        String wateringFrequency,
        String fertilizerGuidance,
        String fertilizerType,
        String fertilizerFrequency,
        String pruningGuidance,
        String propagationInstructions,
        String healthDiagnosis,
        String goalSuggestions,
        String speciesOverview,
        List<String> uses,
        OffsetDateTime completedAt,
        String failureReason
) {}
