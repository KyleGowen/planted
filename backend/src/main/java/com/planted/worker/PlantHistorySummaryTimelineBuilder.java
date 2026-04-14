package com.planted.worker;

import com.planted.entity.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Formats owner journal entries and care events into a single text block for the history-summary LLM prompt.
 * Package-visible for unit tests.
 */
public final class PlantHistorySummaryTimelineBuilder {

    static final DateTimeFormatter ENTRY_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm");

    private PlantHistorySummaryTimelineBuilder() {
    }

    public static String buildTimelineText(
            List<PlantHistoryEntry> journalOldestFirst,
            List<PlantWateringEvent> wateringsNewestFirst,
            List<PlantFertilizerEvent> fertilizersNewestFirst,
            List<PlantPruneEvent> prunesNewestFirst,
            int maxCarePerType) {

        StringBuilder out = new StringBuilder();
        out.append("=== Owner journal (oldest first) ===\n");
        if (journalOldestFirst.isEmpty()) {
            out.append("(no text or photo journal entries)\n");
        } else {
            for (PlantHistoryEntry e : journalOldestFirst) {
                out.append(formatJournalLine(e)).append('\n');
            }
        }

        out.append("\n=== Care events (oldest first, up to ").append(maxCarePerType).append(" per type) ===\n");
        List<CareLine> care = mergeCareEvents(wateringsNewestFirst, fertilizersNewestFirst, prunesNewestFirst, maxCarePerType);
        if (care.isEmpty()) {
            out.append("(no watering, fertilizer, or prune events recorded)\n");
        } else {
            care.sort(Comparator.comparing(CareLine::at));
            for (CareLine line : care) {
                out.append(line.text()).append('\n');
            }
        }

        return out.toString().trim();
    }

    static String formatJournalLine(PlantHistoryEntry entry) {
        String when = entry.getCreatedAt().format(ENTRY_DATE);
        boolean hasNote = entry.getNoteText() != null && !entry.getNoteText().isBlank();
        boolean hasImg = entry.getImageId() != null;
        StringBuilder line = new StringBuilder(when).append(": ");
        if (hasNote) {
            line.append(entry.getNoteText().trim());
        }
        if (hasImg) {
            if (hasNote) {
                line.append(' ');
            }
            line.append("[photo attached]");
        }
        return line.toString();
    }

    private static List<CareLine> mergeCareEvents(
            List<PlantWateringEvent> wateringsNewestFirst,
            List<PlantFertilizerEvent> fertilizersNewestFirst,
            List<PlantPruneEvent> prunesNewestFirst,
            int maxPerType) {

        List<CareLine> lines = new ArrayList<>();

        int w = 0;
        for (PlantWateringEvent e : wateringsNewestFirst) {
            if (w++ >= maxPerType) break;
            String extra = e.getNotes() != null && !e.getNotes().isBlank() ? " — " + e.getNotes().trim() : "";
            lines.add(new CareLine(e.getWateredAt(),
                    "- Watered" + extra));
        }

        int f = 0;
        for (PlantFertilizerEvent e : fertilizersNewestFirst) {
            if (f++ >= maxPerType) break;
            String type = e.getFertilizerType() != null && !e.getFertilizerType().isBlank()
                    ? " (" + e.getFertilizerType().trim() + ")" : "";
            String extra = e.getNotes() != null && !e.getNotes().isBlank() ? " — " + e.getNotes().trim() : "";
            lines.add(new CareLine(e.getFertilizedAt(),
                    "- Fertilized" + type + extra));
        }

        int p = 0;
        for (PlantPruneEvent e : prunesNewestFirst) {
            if (p++ >= maxPerType) break;
            String extra = e.getNotes() != null && !e.getNotes().isBlank() ? " — " + e.getNotes().trim() : "";
            String img = e.getImageId() != null ? " [photo attached]" : "";
            lines.add(new CareLine(e.getPrunedAt(),
                    "- Pruned" + extra + img));
        }

        return lines;
    }

    private record CareLine(OffsetDateTime at, String text) {
    }
}
