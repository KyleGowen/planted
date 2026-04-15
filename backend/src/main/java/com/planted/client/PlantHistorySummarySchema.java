package com.planted.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlantHistorySummarySchema {

    /**
     * One narrative per local calendar day that had activity, newest day first (ISO date descending).
     */
    @JsonProperty("daily_digests")
    private List<PlantHistoryDailyDigestJson> dailyDigests;

    /**
     * Flattened text for {@code info_panel_summary} (legacy queries and plain-text consumers).
     */
    public String flattenForStorage() {
        if (dailyDigests == null || dailyDigests.isEmpty()) {
            return "";
        }
        return dailyDigests.stream()
                .filter(d -> d.getDay() != null && !d.getDay().isBlank()
                        && d.getDigest() != null && !d.getDigest().isBlank())
                .sorted(Comparator.comparing(PlantHistoryDailyDigestJson::getDay).reversed())
                .map(d -> d.getDay() + ": " + d.getDigest().trim())
                .collect(Collectors.joining("\n\n"));
    }
}
