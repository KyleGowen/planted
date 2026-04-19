package com.planted.weather;

import java.util.Optional;

/**
 * Looks up approximate coordinates for a human-entered location (city + optional
 * state/country). Backs the outdoor plant reminder pipeline so users don't have
 * to enter raw lat/lon.
 */
public interface GeocodingService {

    /**
     * @return latitude/longitude for the best match, or empty when the input is
     *         too vague to resolve or the upstream service fails.
     */
    Optional<GeoCoordinates> geocode(String city, String state, String country);
}
