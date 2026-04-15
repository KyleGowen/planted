package com.planted.worker;

import com.planted.entity.*;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlantHistorySummaryDayGroupBuilderTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    @Test
    void groupsCareAndJournalByLocalDay_newestDayFirst() {
        OffsetDateTime apr14 = OffsetDateTime.parse("2026-04-14T23:54:00-04:00");
        OffsetDateTime apr14b = OffsetDateTime.parse("2026-04-14T18:00:00-04:00");
        OffsetDateTime apr13 = OffsetDateTime.parse("2026-04-13T12:00:00-04:00");

        PlantWateringEvent w1 = PlantWateringEvent.builder().plantId(1L).wateredAt(apr14).build();
        PlantWateringEvent w2 = PlantWateringEvent.builder().plantId(1L).wateredAt(apr14b).build();
        PlantFertilizerEvent f1 = PlantFertilizerEvent.builder().plantId(1L).fertilizedAt(apr14).build();
        PlantPruneEvent p1 = PlantPruneEvent.builder().plantId(1L).prunedAt(apr14).build();

        PlantHistoryEntry journal = PlantHistoryEntry.builder()
                .plantId(1L)
                .noteText("Noted yellow leaves")
                .createdAt(apr13)
                .build();

        String text = PlantHistorySummaryDayGroupBuilder.buildGroupedSection(
                NY,
                List.of(journal),
                List.of(w1, w2),
                List.of(f1),
                List.of(p1),
                20);

        assertThat(text).contains("timezone: America/New_York");
        assertThat(text).contains("2026-04-14");
        assertThat(text).contains("watering=2");
        assertThat(text).contains("fertilizer=1");
        assertThat(text).contains("prune=1");
        assertThat(text).contains("2026-04-13");
        assertThat(text).contains("Noted yellow leaves");
        int idxApr14 = text.indexOf("2026-04-14");
        int idxApr13 = text.indexOf("2026-04-13");
        assertThat(idxApr14).isLessThan(idxApr13);
    }
}
