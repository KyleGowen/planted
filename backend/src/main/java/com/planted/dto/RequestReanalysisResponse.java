package com.planted.dto;

import java.time.OffsetDateTime;

public record RequestReanalysisResponse(
        Long analysisId,
        Long plantId,
        String status,
        OffsetDateTime createdAt
) {}
