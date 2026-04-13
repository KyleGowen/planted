package com.planted.dto;

import java.time.OffsetDateTime;

public record CreatePlantResponse(
        Long plantId,
        String status,
        OffsetDateTime createdAt,
        Long analysisId
) {}
