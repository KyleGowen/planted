package com.planted.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planted.config.PlantedWeatherProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OpenMeteoGeocodingClientParseTest {

    private final ObjectMapper om = new ObjectMapper();
    private final OpenMeteoGeocodingClient client =
            new OpenMeteoGeocodingClient(WebClient.builder(), om, new PlantedWeatherProperties());

    @Test
    void pickBestMatch_prefersStateAndCountryMatches() throws Exception {
        String json = """
                {
                  "results": [
                    {"name":"Portland","latitude":43.66,"longitude":-70.25,"country":"United States","country_code":"US","admin1":"Maine"},
                    {"name":"Portland","latitude":45.52,"longitude":-122.67,"country":"United States","country_code":"US","admin1":"Oregon"},
                    {"name":"Portland","latitude":-38.34,"longitude":141.60,"country":"Australia","country_code":"AU","admin1":"Victoria"}
                  ]
                }
                """;

        Optional<GeoCoordinates> match = client.pickBestMatch(json, "Oregon", "United States");

        assertThat(match).isPresent();
        assertThat(match.get().latitude()).isEqualTo(45.52);
        assertThat(match.get().longitude()).isEqualTo(-122.67);
    }

    @Test
    void pickBestMatch_fallsBackToFirstWhenNoStateOrCountryProvided() throws Exception {
        String json = """
                {
                  "results": [
                    {"name":"Seattle","latitude":47.6062,"longitude":-122.3321,"country":"United States","country_code":"US","admin1":"Washington"}
                  ]
                }
                """;

        Optional<GeoCoordinates> match = client.pickBestMatch(json, null, null);

        assertThat(match).isPresent();
        assertThat(match.get().latitude()).isEqualTo(47.6062);
    }

    @Test
    void pickBestMatch_returnsEmptyWhenNoResults() throws Exception {
        String json = "{\"generationtime_ms\":0.1}";
        assertThat(client.pickBestMatch(json, null, null)).isEmpty();
    }

    @Test
    void geocode_returnsEmptyWhenCityMissing() {
        assertThat(client.geocode(null, "WA", "US")).isEmpty();
        assertThat(client.geocode("  ", "WA", "US")).isEmpty();
    }

    @Test
    void geocodeFreeform_returnsEmptyWhenBlank() {
        assertThat(client.geocode((String) null)).isEmpty();
        assertThat(client.geocode("   ")).isEmpty();
    }
}
