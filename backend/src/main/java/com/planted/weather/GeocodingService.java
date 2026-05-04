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

    /**
     * Free-form variant used when we only have a single address string (e.g. the
     * user's saved physical address). The upstream geocoder accepts natural
     * inputs like "Portland, OR" directly.
     *
     * @return latitude/longitude for the best match, or empty when the input is
     *         blank or the upstream service fails.
     */
    Optional<GeoCoordinates> geocode(String freeform);
}
