package com.planted.dto;

import java.time.OffsetDateTime;

public record PlantHistoryEntryDto(
        Long id,
        /** JOURNAL, WATERING, FERTILIZER, or PRUNE — disambiguates ids across tables for clients. */
        String entryKind,
        String noteText,
        PlantImageDto image,
        /** When this event occurred (journal createdAt, or wateredAt / fertilizedAt / prunedAt). */
        OffsetDateTime createdAt
) {}
