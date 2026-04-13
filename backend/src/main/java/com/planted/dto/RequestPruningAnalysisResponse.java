package com.planted.dto;

import java.time.OffsetDateTime;

public record RequestPruningAnalysisResponse(
        Long analysisId,
        Long plantId,
        String status,
        int imagesUploaded,
        OffsetDateTime createdAt
) {}
