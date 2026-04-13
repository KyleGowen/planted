package com.planted.dto;

import java.time.OffsetDateTime;

public record PlantHistoryEntryDto(
        Long id,
        String noteText,
        PlantImageDto image,
        OffsetDateTime createdAt
) {}
