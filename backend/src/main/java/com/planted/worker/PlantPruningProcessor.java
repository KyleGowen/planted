package com.planted.worker;

import com.planted.client.OpenAiPlantClient;
import com.planted.client.PruningAnalysisSchema;
import com.planted.entity.*;
import com.planted.queue.PlantJobMessage;
import com.planted.repository.*;
import com.planted.service.UserPhysicalAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlantPruningProcessor {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final PlantRepository plantRepository;
    private final PlantAnalysisRepository analysisRepository;
    private final PlantImageRepository imageRepository;
    private final PlantWateringEventRepository wateringEventRepository;
    private final PlantFertilizerEventRepository fertilizerEventRepository;
    private final PlantPruneEventRepository pruneEventRepository;
    private final PlantHistoryEntryRepository historyEntryRepository;
    private final OpenAiPlantClient openAiClient;
    private final UserPhysicalAddressService userPhysicalAddressService;

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
            String pruningGuidance = resolveSpeciesPruningGuidance(registrationAnalysis);

            String careHistory = buildCareHistory(plantId);
            String historyNotes = buildHistoryNotes(plantId);

            log.info("Running pruning analysis for plant {} with {} image(s)", plantId, imagesBase64.size());

            String ownerAddress = userPhysicalAddressService.resolveAddressForPlant(plant)
                    .orElse(null);
            PruningAnalysisSchema result = openAiClient.analyzePruning(
                    imagesBase64, mimeTypes,
                    genus, species,
                    plant.getGoalsText(), pruningGuidance,
                    careHistory, historyNotes,
                    ownerAddress,
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

    private String buildCareHistory(Long plantId) {
        String lastWatered = wateringEventRepository
                .findFirstByPlantIdOrderByWateredAtDesc(plantId)
                .map(e -> e.getWateredAt().format(DATE_FMT))
                .orElse("Never recorded");

        String lastFertilized = fertilizerEventRepository
                .findFirstByPlantIdOrderByFertilizedAtDesc(plantId)
                .map(e -> e.getFertilizedAt().format(DATE_FMT))
                .orElse("Never recorded");

        String lastPruned = pruneEventRepository
                .findFirstByPlantIdOrderByPrunedAtDesc(plantId)
                .map(e -> e.getPrunedAt().format(DATE_FMT))
                .orElse("Never recorded");

        return "Last watered: " + lastWatered + ". "
                + "Last fertilized: " + lastFertilized + ". "
                + "Last pruned: " + lastPruned + ".";
    }

    /**
     * Builds a formatted string of the 5 most recent owner-written text history notes.
     */
    private String buildHistoryNotes(Long plantId) {
        List<com.planted.entity.PlantHistoryEntry> notes =
                historyEntryRepository.findTop5ByPlantIdAndNoteTextIsNotNullOrderByCreatedAtDesc(plantId);
        if (notes.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (com.planted.entity.PlantHistoryEntry note : notes) {
            sb.append("- [").append(note.getCreatedAt().format(DATE_FMT)).append("] ")
              .append(note.getNoteText()).append("\n");
        }
        return sb.toString().trim();
    }

    private static String resolveSpeciesPruningGuidance(PlantAnalysis registrationAnalysis) {
        if (registrationAnalysis == null) {
            return null;
        }
        String general = registrationAnalysis.getPruningGeneralGuidance();
        if (general != null && !general.isBlank()) {
            return general;
        }
        return registrationAnalysis.getPruningGuidance();
    }
}
