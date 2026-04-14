package com.planted.worker;

import com.planted.entity.PlantFertilizerEvent;
import com.planted.entity.PlantHistoryEntry;
import com.planted.entity.PlantPruneEvent;
import com.planted.entity.PlantWateringEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlantHistorySummaryTimelineBuilderTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2024, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T1 = OffsetDateTime.of(2024, 6, 15, 9, 30, 0, 0, ZoneOffset.UTC);

    @Test
    void formatJournalLine_noteAndPhoto() {
        PlantHistoryEntry e = PlantHistoryEntry.builder()
                .id(1L)
                .plantId(1L)
                .noteText("Leaves drooping")
                .imageId(99L)
                .createdAt(T0)
                .build();
        assertThat(PlantHistorySummaryTimelineBuilder.formatJournalLine(e))
                .contains("June 1, 2024")
                .contains("Leaves drooping")
                .contains("[photo attached]");
    }

    @Test
    void buildTimelineText_mergesCareOldestFirst() {
        PlantHistoryEntry journal = PlantHistoryEntry.builder()
                .id(1L)
                .plantId(1L)
                .noteText("Repotted")
                .createdAt(T0)
                .build();

        PlantWateringEvent w = PlantWateringEvent.builder()
                .plantId(1L)
                .wateredAt(T1)
                .notes(null)
                .build();

        String text = PlantHistorySummaryTimelineBuilder.buildTimelineText(
                List.of(journal),
                List.of(w),
                List.of(),
                List.of(),
                20);

        assertThat(text).contains("Owner journal");
        assertThat(text).contains("Repotted");
        assertThat(text).contains("Care events");
        assertThat(text).contains("Watered");
        // watering merged list is sorted ascending: June 1 journal then June 15 water
        int journalIdx = text.indexOf("Repotted");
        int waterIdx = text.indexOf("Watered");
        assertThat(journalIdx).isLessThan(waterIdx);
    }
}
