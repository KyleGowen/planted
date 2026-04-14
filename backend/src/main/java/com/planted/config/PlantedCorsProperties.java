package com.planted.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "planted.cors")
public record PlantedCorsProperties(List<String> allowedOriginPatterns) {
}
