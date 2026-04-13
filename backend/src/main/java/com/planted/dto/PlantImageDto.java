package com.planted.dto;

import java.time.OffsetDateTime;

public record PlantImageDto(
        Long id,
        String imageType,
        String url,
        String mimeType,
        OffsetDateTime capturedAt,
        int sortOrder
) {}
