package com.planted.service;

import com.planted.config.PlantedHistoryProperties;
import net.iakovlev.timeshape.TimeZoneEngine;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class HistorySummaryDayZoneResolverTest {

    @Test
    void resolveZone_usesCoordinatesWhenPresent() {
        PlantedHistoryProperties props = new PlantedHistoryProperties();
        props.setDayBoundaryZone("UTC");
        TimeZoneEngine engine = TimeZoneEngine.initialize();
        HistorySummaryDayZoneResolver resolver = new HistorySummaryDayZoneResolver(props, engine);

        ZoneId z = resolver.resolveZone(40.7128, -74.0060);
        assertThat(z.getId()).isEqualTo("America/New_York");
    }

    @Test
    void resolveZone_fallsBackWhenCoordinatesNull() {
        PlantedHistoryProperties props = new PlantedHistoryProperties();
        props.setDayBoundaryZone("Europe/Berlin");
        HistorySummaryDayZoneResolver resolver = new HistorySummaryDayZoneResolver(props, TimeZoneEngine.initialize());

        assertThat(resolver.resolveZone(null, null)).isEqualTo(ZoneId.of("Europe/Berlin"));
    }
}
