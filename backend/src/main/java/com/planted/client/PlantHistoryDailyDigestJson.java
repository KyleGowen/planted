package com.planted.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlantHistoryDailyDigestJson {
    /** Local calendar day in ISO format (YYYY-MM-DD). */
    private String day;
    /** Narrative digest for that day. */
    private String digest;
}
