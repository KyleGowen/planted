package com.planted.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record PlantDetailResponse(
        Long id,
        String name,
        String genus,
        String species,
        String variety,
        String speciesLabel,
        String location,
        String goalsText,
        String geoCountry,
        String geoState,
        String geoCity,
        String status,
        PlantImageDto illustratedImage,
        List<PlantImageDto> originalImages,
        List<PlantImageDto> healthyReferenceImages,
        List<PlantImageDto> pruneUpdateImages,
        AnalysisSummaryDto latestAnalysis,
        ReminderStateDto reminderState,
        boolean hasActiveJobs,
        List<PlantHistoryEntryDto> historyEntries,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
