package com.planted.worker;

import com.planted.client.OpenAiPlantClient;
import com.planted.client.PruningAnalysisSchema;
import com.planted.entity.*;
import com.planted.queue.PlantJobMessage;
import com.planted.repository.*;
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
public class PlantPruningProcessor {

    private final PlantRepository plantRepository;
    private final PlantAnalysisRepository analysisRepository;
    private final PlantImageRepository imageRepository;
    private final OpenAiPlantClient openAiClient;

    @Transactional
    public void process(PlantJobMessage message) {
        Long plantId = message.getPlantId();
        Long analysisId = message.getAnalysisId();

        PlantAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Pruning analysis not found: " + analysisId));

        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));

        // Get the latest completed registration analysis for species/care context
        PlantAnalysis registrationAnalysis = analysisRepository
                .findFirstByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(
                        plantId, PlantAnalysis.AnalysisType.REGISTRATION)
                .filter(a -> a.getStatus() == PlantAnalysis.AnalysisStatus.COMPLETED)
                .orElse(null);

        analysis.setStatus(PlantAnalysis.AnalysisStatus.PROCESSING);
        analysisRepository.save(analysis);

        try {
            // Load images for pruning analysis
            List<String> imagesBase64 = new ArrayList<>();
            List<String> mimeTypes = new ArrayList<>();

            List<Long> imageIds = message.getImageIds();
            if (imageIds == null || imageIds.isEmpty()) {
                throw new IllegalArgumentException("No images provided for pruning analysis");
            }

            for (Long imageId : imageIds) {
                PlantImage image = imageRepository.findById(imageId)
                        .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));
                try {
                    byte[] bytes = Files.readAllBytes(Path.of("./data/images", image.getStoragePath()));
                    imagesBase64.add(Base64.getEncoder().encodeToString(bytes));
                    mimeTypes.add(image.getMimeType() != null ? image.getMimeType() : "image/jpeg");
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load image " + imageId, e);
                }
            }

            String genus = registrationAnalysis != null ? registrationAnalysis.getGenus() : plant.getGenus();
            String species = registrationAnalysis != null ? registrationAnalysis.getSpecies() : plant.getSpecies();
            String pruningGuidance = registrationAnalysis != null ? registrationAnalysis.getPruningGuidance() : null;

            log.info("Running pruning analysis for plant {} with {} image(s)", plantId, imagesBase64.size());

            PruningAnalysisSchema result = openAiClient.analyzePruning(
                    imagesBase64, mimeTypes,
                    genus, species,
                    plant.getGoalsText(), pruningGuidance,
                    plantId, analysisId);

            // Store result in pruning analysis record
            analysis.setPruningGuidance(result.getVerdict());
            analysis.setGoalSuggestions(result.getGoalAlignment());
            analysis.setHealthDiagnosis(result.getNotes());

            Map<String, Object> rawResponse = new HashMap<>();
            rawResponse.put("pruningNeeded", result.isPruningNeeded());
            rawResponse.put("verdict", result.getVerdict());
            rawResponse.put("pruningAmount", result.getPruningAmount());
            rawResponse.put("specificRecommendations", result.getSpecificRecommendations());
            rawResponse.put("confidence", result.getConfidence());
            analysis.setRawModelResponseJsonb(rawResponse);

            analysis.setStatus(PlantAnalysis.AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(OffsetDateTime.now());
            analysisRepository.save(analysis);

            log.info("Pruning analysis completed for plant {}: pruningNeeded={}, verdict={}",
                    plantId, result.isPruningNeeded(), result.getVerdict());

        } catch (Exception e) {
            log.error("Pruning analysis failed for plant {}", plantId, e);
            analysis.setStatus(PlantAnalysis.AnalysisStatus.FAILED);
            analysis.setFailureReason(e.getMessage());
            analysisRepository.save(analysis);
        }
    }
}
