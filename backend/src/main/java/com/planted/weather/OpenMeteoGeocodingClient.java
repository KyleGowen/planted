package com.planted.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planted.config.PlantedWeatherProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geocodes a city (+ optional state/country) via Open-Meteo's free geocoding API.
 * Results are cached in-memory keyed by normalized input so repeated saves don't
 * hammer the upstream service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenMeteoGeocodingClient implements GeocodingService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final PlantedWeatherProperties properties;

    private final ConcurrentHashMap<String, Optional<GeoCoordinates>> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<GeoCoordinates> geocode(String city, String state, String country) {
        String cityTrim = trimToNull(city);
        if (cityTrim == null) {
            // City alone is "too coarse" country-only lookups, so require a city.
            return Optional.empty();
        }
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        String stateTrim = trimToNull(state);
        String countryTrim = trimToNull(country);
        String key = cacheKey(cityTrim, stateTrim, countryTrim);
        Optional<GeoCoordinates> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        Optional<GeoCoordinates> result = fetch(cityTrim, stateTrim, countryTrim);
        cache.put(key, result);
        return result;
    }

    @Override
    public Optional<GeoCoordinates> geocode(String freeform) {
        String trimmed = trimToNull(freeform);
        if (trimmed == null) {
            return Optional.empty();
        }
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        String key = "freeform|" + trimmed.toLowerCase(Locale.ROOT);
        Optional<GeoCoordinates> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        Optional<GeoCoordinates> result = fetch(trimmed, null, null);
        cache.put(key, result);
        return result;
    }

    private Optional<GeoCoordinates> fetch(String name, String state, String country) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(properties.getOpenMeteoGeocodingUrl())
                    .queryParam("name", name)
                    .queryParam("count", 10)
                    .queryParam("language", "en")
                    .queryParam("format", "json");
            String uri = builder.build().toUriString();

            String body = webClientBuilder.build()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            return pickBestMatch(body, state, country);
        } catch (Exception e) {
            log.warn("Open-Meteo geocoding failed for name='{}', state='{}', country='{}': {}",
                    name, state, country, e.getMessage());
            return Optional.empty();
        }
    }

    Optional<GeoCoordinates> pickBestMatch(String jsonBody, String state, String country) throws Exception {
        JsonNode root = objectMapper.readTree(jsonBody);
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return Optional.empty();
        }

        JsonNode bestMatch = null;
        int bestScore = Integer.MIN_VALUE;
        for (JsonNode candidate : results) {
            int score = scoreCandidate(candidate, state, country);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
        }
        if (bestMatch == null) {
            return Optional.empty();
        }

        double lat = bestMatch.path("latitude").asDouble(Double.NaN);
        double lon = bestMatch.path("longitude").asDouble(Double.NaN);
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            return Optional.empty();
        }
        return Optional.of(new GeoCoordinates(lat, lon));
    }

    private static int scoreCandidate(JsonNode candidate, String state, String country) {
        int score = 0;
        if (country != null) {
            String countryField = candidate.path("country").asText("");
            String countryCode = candidate.path("country_code").asText("");
            String c = country.toLowerCase(Locale.ROOT);
            if (countryField.equalsIgnoreCase(country) || countryCode.equalsIgnoreCase(country)) {
                score += 4;
            } else if (!countryField.isEmpty() && countryField.toLowerCase(Locale.ROOT).contains(c)) {
                score += 2;
            } else if (!countryCode.isEmpty() && countryCode.toLowerCase(Locale.ROOT).contains(c)) {
                score += 2;
            }
        }
        if (state != null) {
            String admin1 = candidate.path("admin1").asText("");
            String s = state.toLowerCase(Locale.ROOT);
            if (admin1.equalsIgnoreCase(state)) {
                score += 3;
            } else if (!admin1.isEmpty() && admin1.toLowerCase(Locale.ROOT).contains(s)) {
                score += 1;
            }
        }
        return score;
    }

    private static String cacheKey(String city, String state, String country) {
        return String.format("%s|%s|%s",
                city.toLowerCase(Locale.ROOT),
                state == null ? "" : state.toLowerCase(Locale.ROOT),
                country == null ? "" : country.toLowerCase(Locale.ROOT));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
