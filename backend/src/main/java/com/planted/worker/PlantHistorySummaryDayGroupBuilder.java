package com.planted.worker;

import com.planted.entity.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Groups journal and care events by local calendar day for the history-summary LLM prompt.
 * Package-visible for unit tests.
 */
public final class PlantHistorySummaryDayGroupBuilder {

    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("h:mm a");

    private PlantHistorySummaryDayGroupBuilder() {
    }

    /**
     * Text block listing each active day with explicit care counts and journal lines (newest local day first).
     */
    public static String buildGroupedSection(
            ZoneId zone,
            List<PlantHistoryEntry> journalOldestFirst,
            List<PlantWateringEvent> wateringsNewestFirst,
            List<PlantFertilizerEvent> fertilizersNewestFirst,
            List<PlantPruneEvent> prunesNewestFirst,
            int maxCarePerType) {

        Map<LocalDate, DayAgg> byDay = new TreeMap<>(Comparator.reverseOrder());

        if (journalOldestFirst != null) {
            for (PlantHistoryEntry e : journalOldestFirst) {
                LocalDate d = e.getCreatedAt().atZoneSameInstant(zone).toLocalDate();
                byDay.computeIfAbsent(d, x -> new DayAgg()).journalLines.add(
                        PlantHistorySummaryTimelineBuilder.formatJournalLine(e));
            }
        }

        List<CareLine> care = mergeCareEvents(wateringsNewestFirst, fertilizersNewestFirst, prunesNewestFirst, maxCarePerType);
        care.sort(Comparator.comparing(CareLine::at));
        for (CareLine line : care) {
            LocalDate d = line.at().atZoneSameInstant(zone).toLocalDate();
            DayAgg agg = byDay.computeIfAbsent(d, x -> new DayAgg());
            String localTime = line.at().atZoneSameInstant(zone).toLocalTime().format(LOCAL_TIME);
            agg.careDetailLines.add(localTime + " — " + line.text());
            switch (line.kind()) {
                case WATER -> agg.waterCount++;
                case FERT -> agg.fertCount++;
                case PRUNE -> agg.pruneCount++;
            }
        }

        if (byDay.isEmpty()) {
            return "(no journal or care events to group)\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Grouped by local calendar day (timezone: ").append(zone.getId()).append(") ===\n");
        for (Map.Entry<LocalDate, DayAgg> e : byDay.entrySet()) {
            LocalDate day = e.getKey();
            DayAgg agg = e.getValue();
            sb.append("\n--- ").append(day).append(" ---\n");
            sb.append("Care counts this day: ");
            sb.append("watering=").append(agg.waterCount);
            sb.append(", fertilizer=").append(agg.fertCount);
            sb.append(", prune=").append(agg.pruneCount);
            sb.append('\n');
            if (!agg.careDetailLines.isEmpty()) {
                sb.append("Care events (local time):\n");
                for (String cl : agg.careDetailLines) {
                    sb.append("  ").append(cl).append('\n');
                }
            }
            if (!agg.journalLines.isEmpty()) {
                sb.append("Journal / notes:\n");
                for (String jl : agg.journalLines) {
                    sb.append("  ").append(jl).append('\n');
                }
            }
        }
        return sb.toString().trim();
    }

    private enum CareKind {
        WATER, FERT, PRUNE
    }

    private record CareLine(OffsetDateTime at, String text, CareKind kind) {
    }

    private static List<CareLine> mergeCareEvents(
            List<PlantWateringEvent> wateringsNewestFirst,
            List<PlantFertilizerEvent> fertilizersNewestFirst,
            List<PlantPruneEvent> prunesNewestFirst,
            int maxPerType) {

        List<CareLine> lines = new ArrayList<>();

        int w = 0;
        if (wateringsNewestFirst != null) {
            for (PlantWateringEvent e : wateringsNewestFirst) {
                if (w++ >= maxPerType) {
                    break;
                }
                String extra = e.getNotes() != null && !e.getNotes().isBlank() ? " — " + e.getNotes().trim() : "";
                lines.add(new CareLine(e.getWateredAt(), "- Watered" + extra, CareKind.WATER));
            }
        }

        int f = 0;
        if (fertilizersNewestFirst != null) {
            for (PlantFertilizerEvent e : fertilizersNewestFirst) {
                if (f++ >= maxPerType) {
                    break;
                }
                String type = e.getFertilizerType() != null && !e.getFertilizerType().isBlank()
                        ? " (" + e.getFertilizerType().trim() + ")" : "";
                String extra = e.getNotes() != null && !e.getNotes().isBlank() ? " — " + e.getNotes().trim() : "";
                lines.add(new CareLine(e.getFertilizedAt(), "- Fertilized" + type + extra, CareKind.FERT));
            }
        }

        int p = 0;
        if (prunesNewestFirst != null) {
            for (PlantPruneEvent e : prunesNewestFirst) {
                if (p++ >= maxPerType) {
                    break;
                }
                String extra = e.getNotes() != null && !e.getNotes().isBlank() ? " — " + e.getNotes().trim() : "";
                String img = e.getImageId() != null ? " [photo attached]" : "";
                lines.add(new CareLine(e.getPrunedAt(), "- Pruned" + extra + img, CareKind.PRUNE));
            }
        }

        return lines;
    }

    private static final class DayAgg {
        int waterCount;
        int fertCount;
        int pruneCount;
        final List<String> careDetailLines = new ArrayList<>();
        final List<String> journalLines = new ArrayList<>();
    }
}
