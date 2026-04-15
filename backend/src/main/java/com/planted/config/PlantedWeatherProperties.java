package com.planted.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "planted.weather")
public class PlantedWeatherProperties {

    /** When false, no HTTP calls are made and reminders skip weather text. */
    private boolean enabled = true;

    private String openMeteoForecastUrl = "https://api.open-meteo.com/v1/forecast";

    /** In-memory cache TTL for identical (rounded) coordinates. */
    private int cacheTtlMinutes = 60;

    /** Sum past-week precip above this (mm) triggers “recent rain” copy. */
    private double recentRainWeekThresholdMm = 12.0;

    /** Precip in the next two daily buckets above this adds “rain expected” copy. */
    private double forecastRainThresholdMm = 3.0;

    /** Absolute max temp (°C) in the last few days that suggests heat stress. */
    private double heatAbsoluteThresholdC = 32.0;

    /** Degrees above prior-week average max to count as a spike. */
    private double heatSpikeDeltaC = 6.0;

    /** Open-Meteo {@code past_days} (recent precipitation / temperatures). */
    private int pastDays = 7;

    /** Open-Meteo {@code forecast_days} (upcoming rain). */
    private int forecastDays = 3;
}
