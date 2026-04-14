package com.planted.dto;

/**
 * {@code createdAt} is ISO-8601 text so clients and Jackson never hit temporal serialization edge cases.
 */
public record RequestHistorySummaryResponse(
        Long analysisId,
        Long plantId,
        String status,
        String createdAt
) {}
