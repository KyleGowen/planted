package com.planted.worker;

import com.planted.client.OpenAiPlantClient;
import com.planted.entity.Plant;
import com.planted.queue.PlantJobMessage;
import com.planted.repository.PlantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates the single-sentence caption shown under the Indoor/Outdoor label from the
 * owner's raw placement notes ({@link Plant#getLocation()}). This is an intentionally
 * narrow LLM call (text-only, its own prompt key) that runs independently of the
 * registration/reanalysis pipeline so that updates to placement notes surface a fresh
 * caption quickly without waiting on a full reanalysis.
 *
 * <p><b>Deprecation note:</b> this processor has been superseded by the
 * {@code PLACEMENT_CARE} bio section (see
 * {@link com.planted.bio.strategies.PlacementCareStrategy} and
 * {@code PlantBioSectionProcessor}). It remains wired up for one release so that the
 * {@code Plant.placement_notes_summary} column stays populated for any older clients
 * still reading it directly. Remove when no readers reference the column.
 */
@Deprecated(since = "V38__plant_bio_sections", forRemoval = true)
@Slf4j
@Component
@RequiredArgsConstructor
public class PlacementNotesSummaryProcessor {

    private final PlantRepository plantRepository;
    private final OpenAiPlantClient openAiClient;

    @Transactional
    public void process(PlantJobMessage message) {
        Long plantId = message.getPlantId();
        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));

        String location = plant.getLocation();
        if (location == null || location.isBlank()) {
            if (plant.getPlacementNotesSummary() != null) {
                plant.setPlacementNotesSummary(null);
                plantRepository.save(plant);
                log.info("Placement notes cleared for plant {}; summary reset", plantId);
            }
            return;
        }

        try {
            String summary = openAiClient.summarizePlacementNotes(location, plantId);
            String normalized = (summary == null || summary.isBlank()) ? null : summary.trim();
            plant.setPlacementNotesSummary(normalized);
            plantRepository.save(plant);
            log.info("Placement notes summary updated for plant {}: {}", plantId, normalized);
        } catch (Exception e) {
            log.error("Placement notes summary failed for plant {}", plantId, e);
        }
    }
}
