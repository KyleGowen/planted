package com.planted.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planted.config.PlantedWeatherProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open-Meteo forecast API with {@code past_days} for recent rain and short forecast.
 * Cached per rounded lat/lon to limit outbound calls during batch recomputes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenMeteoWeatherClient implements WeatherService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final PlantedWeatherProperties properties;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(WeatherSnapshot snapshot, long expiresAtEpochMs) {}

    @Override
    public Optional<WeatherSnapshot> fetchSnapshot(double latitude, double longitude) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return Optional.empty();
        }
        String key = cacheKey(latitude, longitude);
        long now = System.currentTimeMillis();
        CacheEntry hit = cache.get(key);
        if (hit != null && hit.expiresAtEpochMs > now) {
            return Optional.of(hit.snapshot());
        }

        try {
            String uri = UriComponentsBuilder.fromUriString(properties.getOpenMeteoForecastUrl())
                    .queryParam("latitude", latitude)
                    .queryParam("longitude", longitude)
                    .queryParam("daily", "precipitation_sum,temperature_2m_max")
                    .queryParam("past_days", Math.max(0, properties.getPastDays()))
                    .queryParam("forecast_days", Math.max(0, properties.getForecastDays()))
                    .build()
                    .toUriString();

            String body = webClientBuilder.build()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            WeatherSnapshot snap = parseDaily(body);
            long ttlMs = properties.getCacheTtlMinutes() * 60_000L;
            cache.put(key, new CacheEntry(snap, now + ttlMs));
            return Optional.of(snap);
        } catch (Exception e) {
            log.warn("Open-Meteo fetch failed for lat={}, lon={}: {}", latitude, longitude, e.getMessage());
            return Optional.empty();
        }
    }

    private static String cacheKey(double lat, double lon) {
        return String.format("%.2f,%.2f", lat, lon);
    }

    WeatherSnapshot parseDaily(String jsonBody) throws Exception {
        JsonNode root = objectMapper.readTree(jsonBody);
        JsonNode daily = root.path("daily");
        JsonNode times = daily.path("time");
        JsonNode precips = daily.path("precipitation_sum");
        JsonNode maxTemps = daily.path("temperature_2m_max");
        if (!times.isArray() || !precips.isArray() || !maxTemps.isArray()) {
            throw new IllegalStateException("Unexpected Open-Meteo daily shape");
        }

        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        int lookbackDays = Math.max(1, properties.getPastDays());
        LocalDate weekAgo = today.minusDays(lookbackDays);

        double weekPrecip = 0;
        double maxLast3 = Double.NEGATIVE_INFINITY;
        double forecastTwoDayPrecip = 0;
        int n = times.size();
        ListMaxTracker priorWeekMaxTemps = new ListMaxTracker();

        for (int i = 0; i < n; i++) {
            LocalDate d = parseDate(times.get(i).asText(null));
            if (d == null) {
                continue;
            }
            double p = precips.path(i).asDouble(0);
            double tmax = maxTemps.path(i).asDouble(Double.NaN);

            if (!d.isBefore(weekAgo) && !d.isAfter(today)) {
                weekPrecip += Math.max(0, p);
            }
            if (!d.isBefore(today.minusDays(2)) && !d.isAfter(today)) {
                if (!Double.isNaN(tmax)) {
                    maxLast3 = Math.max(maxLast3, tmax);
                }
            }
            if (d.isAfter(today) && !d.isAfter(today.plusDays(2))) {
                forecastTwoDayPrecip += Math.max(0, p);
            }
            if (d.isBefore(today.minusDays(2)) && !d.isBefore(weekAgo) && !Double.isNaN(tmax)) {
                priorWeekMaxTemps.add(tmax);
            }
        }

        if (maxLast3 == Double.NEGATIVE_INFINITY) {
            maxLast3 = Double.NaN;
        }
        double baseline = priorWeekMaxTemps.averageOrNaN();
        boolean heat = (!Double.isNaN(maxLast3) && maxLast3 >= properties.getHeatAbsoluteThresholdC())
                || (!Double.isNaN(maxLast3) && !Double.isNaN(baseline)
                && maxLast3 >= baseline + properties.getHeatSpikeDeltaC());

        return new WeatherSnapshot(weekPrecip, maxLast3, forecastTwoDayPrecip, heat);
    }

    private static LocalDate parseDate(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static final class ListMaxTracker {
        private double sum;
        private int count;

        void add(double v) {
            sum += v;
            count++;
        }

        double averageOrNaN() {
            return count == 0 ? Double.NaN : sum / count;
        }
    }
}
