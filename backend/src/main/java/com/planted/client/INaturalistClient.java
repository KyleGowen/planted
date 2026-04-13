package com.planted.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches reference photo URLs from the iNaturalist taxa API.
 * No authentication required for basic taxa lookups.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class INaturalistClient {

    private static final String INATURALIST_API = "https://api.inaturalist.org/v1";
    private static final int MAX_PHOTOS = 3;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * Returns up to {@code MAX_PHOTOS} medium-sized reference photo URLs for the given species.
     * Returns an empty list if the taxon is not found or the API is unavailable.
     */
    public List<String> fetchReferencePhotoUrls(String genus, String species) {
        String query = buildQuery(genus, species);
        List<String> urls = new ArrayList<>();

        try {
            WebClient client = webClientBuilder
                    .baseUrl(INATURALIST_API)
                    .defaultHeader("User-Agent", "Planted-App/1.0")
                    .build();

            String responseBody = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/taxa")
                            .queryParam("q", query)
                            .queryParam("per_page", 1)
                            .queryParam("photos", true)
                            .queryParam("is_active", true)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null) return urls;

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.path("results");

            if (results.isEmpty()) {
                log.info("No iNaturalist taxa found for query: {}", query);
                return urls;
            }

            JsonNode taxon = results.get(0);
            JsonNode taxonPhotos = taxon.path("taxon_photos");

            for (int i = 0; i < taxonPhotos.size() && urls.size() < MAX_PHOTOS; i++) {
                String url = taxonPhotos.get(i).path("photo").path("medium_url").asText(null);
                if (url != null && !url.isBlank()) {
                    urls.add(url);
                }
            }

            // Fall back to default_photo if taxon_photos didn't provide enough
            if (urls.isEmpty()) {
                String defaultUrl = taxon.path("default_photo").path("medium_url").asText(null);
                if (defaultUrl != null && !defaultUrl.isBlank()) {
                    urls.add(defaultUrl);
                }
            }

            log.info("Fetched {} reference photo URL(s) from iNaturalist for {}", urls.size(), query);
        } catch (Exception e) {
            log.warn("Failed to fetch reference photos from iNaturalist for {}: {}", query, e.getMessage());
        }

        return urls;
    }

    private String buildQuery(String genus, String species) {
        if (species != null && !species.isBlank()) {
            return genus + " " + species;
        }
        return genus;
    }
}
