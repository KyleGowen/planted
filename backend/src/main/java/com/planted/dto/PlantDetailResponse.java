package com.planted.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PlantDetailResponse(
        Long id,
        String name,
        String genus,
        String species,
        String variety,
        String speciesLabel,
        String location,
        /** One-sentence LLM paraphrase of {@code location}; null when notes are missing or the dedicated summary job has not completed. */
        String placementNotesSummary,
        String goalsText,
        String geoCountry,
        String geoState,
        String geoCity,
        String growingContext,
        Double latitude,
        Double longitude,
        String status,
        PlantImageDto illustratedImage,
        List<PlantImageDto> originalImages,
        List<PlantImageDto> healthyReferenceImages,
        List<PlantImageDto> pruneUpdateImages,
        AnalysisSummaryDto latestAnalysis,
        /**
         * Decomposed bio sections keyed by {@link com.planted.entity.PlantBioSectionKey} name.
         * Preferred over {@code latestAnalysis} by the frontend; {@code latestAnalysis} remains
         * populated as a legacy fallback during the backfill window.
         */
        Map<String, BioSectionDto> bioSections,
        ReminderStateDto reminderState,
        boolean hasActiveJobs,
        List<PlantHistoryEntryDto> historyEntries,
        String historySummaryText,
        /** Per-local-day narratives from the latest completed history summary job (newest day first). Empty for legacy summaries. */
        List<HistoryDailyDigestDto> historyDailyDigests,
        OffsetDateTime historySummaryCompletedAt,
        String historySummaryError,
        /** True when the plant has journal entries or at least one care event (required to enqueue a history summary). */
        boolean historySummaryEligible,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
