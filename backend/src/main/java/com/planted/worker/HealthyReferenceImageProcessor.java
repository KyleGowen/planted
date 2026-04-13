package com.planted.worker;

import com.planted.client.INaturalistClient;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fetches and stores healthy reference image URLs for the plant species
 * using the iNaturalist taxa API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthyReferenceImageProcessor {

    private final PlantRepository plantRepository;
    private final PlantAnalysisRepository analysisRepository;
    private final PlantImageRepository imageRepository;
    private final INaturalistClient iNaturalistClient;

    @Transactional
    public void process(PlantJobMessage message) {
        Long plantId = message.getPlantId();

        plantRepository.findById(plantId)
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

        List<String> photoUrls = iNaturalistClient.fetchReferencePhotoUrls(
                analysis.getGenus(), analysis.getSpecies());

        if (photoUrls.isEmpty()) {
            log.warn("No reference photos found for plant {} ({} {})",
                    plantId, analysis.getGenus(), analysis.getSpecies());
            return;
        }

        Set<String> existingPaths = imageRepository
                .findByPlantIdAndImageTypeOrderBySortOrderAscCreatedAtAsc(
                        plantId, PlantImage.ImageType.HEALTHY_REFERENCE)
                .stream()
                .map(PlantImage::getStoragePath)
                .collect(Collectors.toSet());

        int sortOrder = existingPaths.size();
        for (String url : photoUrls) {
            if (!existingPaths.contains(url)) {
                PlantImage refImage = PlantImage.builder()
                        .plantId(plantId)
                        .imageType(PlantImage.ImageType.HEALTHY_REFERENCE)
                        .storageType(PlantImage.StorageType.URL)
                        .storagePath(url)
                        .mimeType("image/jpeg")
                        .capturedAt(OffsetDateTime.now())
                        .sortOrder(sortOrder++)
                        .build();
                imageRepository.save(refImage);
            }
        }

        log.info("Stored {} reference photo(s) for plant {} ({} {})",
                photoUrls.size(), plantId, analysis.getGenus(), analysis.getSpecies());
    }
}
