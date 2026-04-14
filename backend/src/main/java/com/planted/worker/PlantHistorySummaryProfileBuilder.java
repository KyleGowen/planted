package com.planted.worker;

import com.planted.entity.Plant;
import com.planted.entity.PlantAnalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Text block for history-summary prompts: placement, goals, geo, and a narrow care snapshot
 * (health, goals, pruning) so the model does not echo routine light/water/fertilizer boilerplate.
 */
public final class PlantHistorySummaryProfileBuilder {

    private PlantHistorySummaryProfileBuilder() {
    }

    public static String buildPlantProfile(Plant plant, PlantAnalysis latestCare) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "Placement / location", plant.getLocation());
        appendIfPresent(sb, "Owner goals", plant.getGoalsText());
        String geo = formatGeographicLine(plant.getGeoCountry(), plant.getGeoState(), plant.getGeoCity());
        appendIfPresent(sb, "Geographic context", geo);

        if (latestCare != null) {
            String careBlock = buildCareSnapshotLines(latestCare);
            if (!careBlock.isEmpty()) {
                sb.append("Latest completed care analysis snapshot (registration or reanalysis):\n");
                sb.append(careBlock);
            }
        }

        return sb.toString().trim();
    }

    static String formatGeographicLine(String geoCountry, String geoState, String geoCity) {
        List<String> parts = new ArrayList<>();
        if (geoCity != null && !geoCity.isBlank()) {
            parts.add(geoCity.trim());
        }
        if (geoState != null && !geoState.isBlank()) {
            parts.add(geoState.trim());
        }
        if (geoCountry != null && !geoCountry.isBlank()) {
            parts.add(geoCountry.trim());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String buildCareSnapshotLines(PlantAnalysis latestCare) {
        StringBuilder b = new StringBuilder();
        appendIfPresent(b, "Health diagnosis", latestCare.getHealthDiagnosis());
        appendIfPresent(b, "Goal suggestions (from analysis)", latestCare.getGoalSuggestions());
        appendIfPresent(b, "Pruning guidance", latestCare.getPruningGuidance());
        return b.toString().trim();
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value.trim()).append('\n');
        }
    }
}
