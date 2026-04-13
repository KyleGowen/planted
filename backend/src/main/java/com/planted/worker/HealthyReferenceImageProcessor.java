package com.planted.worker;

import com.planted.entity.Plant;
import com.planted.entity.PlantAnalysis;
import com.planted.entity.PlantImage;
import com.planted.queue.PlantJobMessage;
import com.planted.repository.PlantAnalysisRepository;
import com.planted.repository.PlantImageRepository;
import com.planted.repository.PlantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fetches and stores healthy reference image URLs for the plant species.
 * Currently stores the search query as a URL-type reference — a future iteration
 * can integrate a real image search API (Unsplash, iNaturalist, etc.).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthyReferenceImageProcessor {

    private final PlantRepository plantRepository;
    private final PlantAnalysisRepository analysisRepository;
    private final PlantImageRepository imageRepository;

    @Transactional
    public void process(PlantJobMessage message) {
        Long plantId = message.getPlantId();

        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));

        PlantAnalysis analysis = analysisRepository
                .findFirstByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(
                        plantId, PlantAnalysis.AnalysisType.REGISTRATION)
                .filter(a -> a.getStatus() == PlantAnalysis.AnalysisStatus.COMPLETED)
                .orElse(null);

        if (analysis == null || analysis.getGenus() == null) {
            log.warn("Cannot fetch reference images for plant {} — analysis not complete", plantId);
            return;
        }

        String genus = analysis.getGenus();
        String species = analysis.getSpecies() != null ? analysis.getSpecies() : "";
        String speciesQuery = (genus + " " + species).trim().replace(" ", "+");

        // Store placeholder reference URLs pointing to iNaturalist-style searches
        // These can be replaced with real fetched images in a future iteration
        List<String> referenceUrls = List.of(
                "https://www.inaturalist.org/taxa/search?q=" + speciesQuery,
                "https://www.gbif.org/species/search?q=" + speciesQuery
        );

        int sortOrder = 0;
        for (String url : referenceUrls) {
            boolean alreadyExists = imageRepository
                    .findByPlantIdAndImageTypeOrderBySortOrderAscCreatedAtAsc(
                            plantId, PlantImage.ImageType.HEALTHY_REFERENCE)
                    .stream()
                    .anyMatch(img -> img.getStoragePath().equals(url));

            if (!alreadyExists) {
                PlantImage refImage = PlantImage.builder()
                        .plantId(plantId)
                        .imageType(PlantImage.ImageType.HEALTHY_REFERENCE)
                        .storageType(PlantImage.StorageType.URL)
                        .storagePath(url)
                        .mimeType("text/html")
                        .capturedAt(OffsetDateTime.now())
                        .sortOrder(sortOrder++)
                        .build();
                imageRepository.save(refImage);
            }
        }

        log.info("Healthy reference images stored for plant {} ({})", plantId, genus + " " + species);
    }
}
