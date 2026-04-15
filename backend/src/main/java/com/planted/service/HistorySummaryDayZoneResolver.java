package com.planted.service;

import com.planted.config.PlantedHistoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.iakovlev.timeshape.TimeZoneEngine;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Resolves the {@link ZoneId} used to bucket history events into calendar days for digest generation.
 * Uses embedded {@link TimeZoneEngine} when the plant has valid latitude/longitude; otherwise falls back
 * to {@link PlantedHistoryProperties#getDayBoundaryZone()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistorySummaryDayZoneResolver {

    private final PlantedHistoryProperties historyProperties;
    private final TimeZoneEngine timeZoneEngine;

    public ZoneId resolveZone(Double latitude, Double longitude) {
        if (latitude != null && longitude != null
                && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0) {
            Optional<ZoneId> found = timeZoneEngine.query(latitude, longitude);
            if (found.isPresent()) {
                return found.get();
            }
            log.debug("No IANA zone for lat={}, lon={}; using fallback {}", latitude, longitude,
                    historyProperties.getDayBoundaryZone());
        }
        return ZoneId.of(historyProperties.getDayBoundaryZone());
    }
}
