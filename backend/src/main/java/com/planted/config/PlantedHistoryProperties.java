package com.planted.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * History summary jobs: calendar-day grouping when plant coordinates do not resolve a zone.
 */
@Data
@ConfigurationProperties(prefix = "planted.history")
public class PlantedHistoryProperties {

    /**
     * IANA timezone id used when the plant has no latitude/longitude or lookup returns empty.
     */
    private String dayBoundaryZone = "UTC";
}
