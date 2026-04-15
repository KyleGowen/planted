package com.planted.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlantHistorySummarySchemaTest {

    @Test
    void flattenForStorage_joinsNewestDayFirst() {
        PlantHistorySummarySchema s = new PlantHistorySummarySchema();
        PlantHistoryDailyDigestJson a = new PlantHistoryDailyDigestJson();
        a.setDay("2026-04-13");
        a.setDigest("Older day.");
        PlantHistoryDailyDigestJson b = new PlantHistoryDailyDigestJson();
        b.setDay("2026-04-14");
        b.setDigest("Newer day.");
        s.setDailyDigests(List.of(a, b));

        assertThat(s.flattenForStorage()).isEqualTo(
                "2026-04-14: Newer day.\n\n2026-04-13: Older day.");
    }
}
