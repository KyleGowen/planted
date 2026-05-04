package com.planted.dto;

import java.time.OffsetDateTime;

/**
 * A single cross-plant activity event for the mobile activity feed.
 * Aggregates watering, fertilizer, prune, and journal entries from all active plants.
 */
public record ActivityEntryDto(
        Long plantId,
        /** Plant's display name (user name, or genus/species fallback). */
        String plantName,
        /** Illustrated image if available; falls back to the most recent original upload. */
        PlantImageDto plantThumbnail,
        /** JOURNAL | WATERING | FERTILIZER | PRUNE */
        String entryKind,
        String noteText,
        /** Non-null for JOURNAL entries with a photo, and for PRUNE entries with a photo. */
        PlantImageDto image,
        OffsetDateTime createdAt
) {}
