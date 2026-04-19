package com.planted.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Serialized view of a single {@code plant_bio_sections} row for the API.
 * Frontend reads the decomposed bio from {@link PlantDetailResponse#bioSections()}
 * and falls back to {@link PlantDetailResponse#latestAnalysis()} while cache
 * rows are being backfilled for legacy plants.
 */
public record BioSectionDto(
        /** Enum name, e.g. SPECIES_ID, WATER_CARE. */
        String key,
        /** PENDING / PROCESSING / COMPLETED / FAILED. */
        String status,
        /** Structured output keyed by the section's schema fields. Null while pending. */
        Map<String, Object> content,
        /** Prompt key that produced this content (for debugging / future migrations). */
        String promptKey,
        /** Prompt version used. */
        Integer promptVersion,
        /** Timestamp the cached content was last written. */
        OffsetDateTime generatedAt,
        /** True when this section is either stale (generated_at cleared or TTL expired) or currently PROCESSING. */
        boolean isRefreshing,
        /** Last error message when status is FAILED. */
        String lastError
) {}
