package com.planted.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Update outdoor vs indoor and optional coordinates for weather-aware reminders.
 */
public record UpdatePlantGrowingRequest(
        @NotNull String growingContext,
        Double latitude,
        Double longitude
) {}
