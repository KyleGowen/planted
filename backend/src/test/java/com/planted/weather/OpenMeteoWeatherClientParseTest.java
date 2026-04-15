package com.planted.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planted.config.PlantedWeatherProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OpenMeteoWeatherClientParseTest {

    @Test
    void parseDaily_sumsLookbackPrecipitationAndDetectsHeat() throws Exception {
        ObjectMapper om = new ObjectMapper();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        ArrayNode times = om.createArrayNode();
        ArrayNode precips = om.createArrayNode();
        ArrayNode maxTemps = om.createArrayNode();
        for (int i = -10; i <= 4; i++) {
            LocalDate d = today.plusDays(i);
            times.add(d.toString());
            if (!d.isAfter(today)) {
                precips.add(5.0);
            } else {
                precips.add(2.0);
            }
            if (d.equals(today)) {
                maxTemps.add(35.0);
            } else {
                maxTemps.add(22.0);
            }
        }

        ObjectNode daily = om.createObjectNode();
        daily.set("time", times);
        daily.set("precipitation_sum", precips);
        daily.set("temperature_2m_max", maxTemps);
        ObjectNode root = om.createObjectNode();
        root.set("daily", daily);
        String json = om.writeValueAsString(root);

        PlantedWeatherProperties props = new PlantedWeatherProperties();
        props.setPastDays(7);
        props.setHeatAbsoluteThresholdC(32);
        props.setHeatSpikeDeltaC(6);

        OpenMeteoWeatherClient client = new OpenMeteoWeatherClient(
                WebClient.builder(), om, props);

        WeatherSnapshot snap = client.parseDaily(json);

        assertThat(snap.pastWeekPrecipitationMm()).isGreaterThanOrEqualTo(35.0);
        assertThat(snap.forecastPrecipNextTwoDaysMm()).isGreaterThanOrEqualTo(4.0);
        assertThat(snap.likelyHeatStress()).isTrue();
    }
}
