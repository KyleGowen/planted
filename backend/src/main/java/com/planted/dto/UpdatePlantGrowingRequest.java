package com.planted.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Update outdoor vs indoor. Coordinates are derived server-side from the plant's
 * stored city/state/country, so only the environment selector is required here.
 */
public record UpdatePlantGrowingRequest(
        @NotNull String growingContext
) {}
