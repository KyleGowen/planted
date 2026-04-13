package com.planted.worker;

import com.planted.client.OpenAiPlantClient;
import com.planted.client.PlantAnalysisSchema;
import com.planted.entity.*;
import com.planted.queue.PlantJobMessage;
import com.planted.queue.PlantJobPublisher;
import com.planted.repository.*;
import com.planted.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlantAnalysisProcessor {

    private final PlantRepository plantRepository;
    private final PlantAnalysisRepository analysisRepository;
    private final PlantImageRepository imageRepository;
    private final OpenAiPlantClient openAiClient;
    private final PlantJobPublisher jobPublisher;

    @Transactional
    public void process(PlantJobMessage message) {
        Long plantId = message.getPlantId();
        Long analysisId = message.getAnalysisId();

        PlantAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));

        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));

        // Mark as processing
        analysis.setStatus(PlantAnalysis.AnalysisStatus.PROCESSING);
        analysisRepository.save(analysis);

        try {
            // Load the primary image
            PlantImage image = getFirstImage(message.getImageIds(), plantId);
            String[] imageData = loadImageAsBase64(image);
            String base64 = imageData[0];
            String mimeType = imageData[1];

            log.info("Running registration analysis for plant {}, analysis {}", plantId, analysisId);
            PlantAnalysisSchema result = openAiClient.analyzeRegistration(
                    base64, mimeType,
                    plant.getGoalsText(), plant.getLocation(),
                    plantId, analysisId);

            // Write normalized fields
            analysis.setClassName(result.getClassName());
            analysis.setGenus(result.getGenus());
            analysis.setSpecies(result.getSpecies());
            analysis.setVariety(result.getVariety());
            analysis.setScientificName(result.getScientificName());
            analysis.setConfidence(result.getConfidence());
            analysis.setNativeRegionsJson(result.getNativeRegions());
            analysis.setLightNeeds(result.getLightNeeds());
            analysis.setPlacementGuidance(result.getPlacementGuidance());
            analysis.setWateringAmount(result.getWateringAmount());
            analysis.setWateringFrequency(result.getWateringFrequency());
            analysis.setWateringGuidance(result.getWateringGuidance());
            analysis.setFertilizerType(result.getFertilizerType());
            analysis.setFertilizerFrequency(result.getFertilizerFrequency());
            analysis.setFertilizerGuidance(result.getFertilizerGuidance());
            analysis.setPruningGuidance(result.getPruningGuidance());
            analysis.setPropagationInstructions(result.getPropagationInstructions());
            analysis.setHealthDiagnosis(result.getHealthDiagnosis());
            analysis.setGoalSuggestions(result.getGoalSuggestions());
            analysis.setInterestingFactsJson(result.getInterestingFacts());
            analysis.setUsesJson(result.getUses());

            // Store raw response in JSONB for future prompt iteration
            Map<String, Object> rawResponse = new HashMap<>();
            rawResponse.put("genus", result.getGenus());
            rawResponse.put("species", result.getSpecies());
            rawResponse.put("confidence", result.getConfidence());
            analysis.setRawModelResponseJsonb(rawResponse);

            analysis.setStatus(PlantAnalysis.AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(OffsetDateTime.now());
            analysisRepository.save(analysis);

            // Denormalize key fields onto the plant root for fast reads
            if (result.getGenus() != null) plant.setGenus(result.getGenus());
            if (result.getSpecies() != null) plant.setSpecies(result.getSpecies());
            if (result.getVariety() != null) plant.setVariety(result.getVariety());
            if (result.getGenus() != null || result.getSpecies() != null) {
                plant.setSpeciesLabel(buildSpeciesLabel(result.getGenus(), result.getSpecies()));
            }
            boolean hasHealthIssue = result.getHealthDiagnosis() != null
                    && !result.getHealthDiagnosis().isBlank()
                    && !result.getHealthDiagnosis().toLowerCase().contains("healthy");
            plant.setHealthAttentionNeeded(hasHealthIssue);
            plantRepository.save(plant);

            // Enqueue reminder recompute now that analysis is complete
            jobPublisher.publish(PlantJobMessage.builder()
                    .jobType(PlantJobMessage.JobType.PLANT_REMINDER_RECOMPUTE)
                    .plantId(plantId)
                    .build());

            // Enqueue healthy reference image fetch
            jobPublisher.publish(PlantJobMessage.builder()
                    .jobType(PlantJobMessage.JobType.HEALTHY_REFERENCE_IMAGE_FETCH)
                    .plantId(plantId)
                    .build());

            log.info("Registration analysis completed for plant {} (genus={}, species={})",
                    plantId, result.getGenus(), result.getSpecies());

        } catch (Exception e) {
            log.error("Registration analysis failed for plant {}", plantId, e);
            analysis.setStatus(PlantAnalysis.AnalysisStatus.FAILED);
            analysis.setFailureReason(e.getMessage());
            analysisRepository.save(analysis);
        }
    }

    private PlantImage getFirstImage(List<Long> imageIds, Long plantId) {
        if (imageIds != null && !imageIds.isEmpty()) {
            return imageRepository.findById(imageIds.get(0))
                    .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageIds.get(0)));
        }
        return imageRepository
                .findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(plantId, PlantImage.ImageType.ORIGINAL_UPLOAD)
                .orElseThrow(() -> new IllegalArgumentException("No image found for plant " + plantId));
    }

    private String[] loadImageAsBase64(PlantImage image) {
        try {
            byte[] bytes;
            if (image.getStorageType() == PlantImage.StorageType.LOCAL) {
                bytes = Files.readAllBytes(Path.of("./data/images", image.getStoragePath()));
            } else {
                throw new UnsupportedOperationException("S3 image loading in worker not yet implemented");
            }
            String mimeType = image.getMimeType() != null ? image.getMimeType() : "image/jpeg";
            return new String[]{Base64.getEncoder().encodeToString(bytes), mimeType};
        } catch (IOException e) {
            throw new RuntimeException("Failed to load image for analysis: " + image.getStoragePath(), e);
        }
    }

    private String buildSpeciesLabel(String genus, String species) {
        if (genus == null && species == null) return null;
        if (species == null) return genus;
        if (genus == null) return species;
        return genus + " " + species;
    }
}
