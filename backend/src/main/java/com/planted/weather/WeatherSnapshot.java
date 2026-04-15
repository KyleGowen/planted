package com.planted.weather;

/**
 * Compact, plant-agnostic facts from Open-Meteo for reminder copy.
 */
public record WeatherSnapshot(
        double pastWeekPrecipitationMm,
        double maxTempLastThreeDaysC,
        double forecastPrecipNextTwoDaysMm,
        boolean likelyHeatStress
) {}
