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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlantAnalysisProcessor {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    @Value("${planted.storage.local-path:./data/images}")
    private String localStorageBasePath;

    private final PlantRepository plantRepository;
    private final PlantAnalysisRepository analysisRepository;
    private final PlantImageRepository imageRepository;
    private final PlantWateringEventRepository wateringEventRepository;
    private final PlantFertilizerEventRepository fertilizerEventRepository;
    private final PlantPruneEventRepository pruneEventRepository;
    private final PlantHistoryEntryRepository historyEntryRepository;
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

            // Gather prior care context from the most recent completed analysis (relevant for reanalysis)
            String priorCareContext = buildPriorCareContext(plantId, analysisId);

            // Gather care event history
            String careHistory = buildCareHistory(plantId);

            // Gather owner history notes
            String historyNotes = buildHistoryNotes(plantId);

            log.info("Running registration analysis for plant {}, analysis {}", plantId, analysisId);
            PlantAnalysisSchema result = openAiClient.analyzeRegistration(
                    base64, mimeType,
                    plant.getGoalsText(), plant.getLocation(),
                    plant.getName(), priorCareContext, careHistory, historyNotes,
                    plant.getGeoCountry(), plant.getGeoState(), plant.getGeoCity(),
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
            analysis.setLightGeneralGuidance(result.getLightGeneralGuidance());
            analysis.setPlacementGuidance(result.getPlacementGuidance());
            analysis.setPlacementGeneralGuidance(result.getPlacementGeneralGuidance());
            analysis.setWateringAmount(result.getWateringAmount());
            analysis.setWateringFrequency(result.getWateringFrequency());
            analysis.setWateringGuidance(result.getWateringGuidance());
            analysis.setFertilizerType(result.getFertilizerType());
            analysis.setFertilizerFrequency(result.getFertilizerFrequency());
            analysis.setFertilizerGuidance(result.getFertilizerGuidance());
            analysis.setPruningActionSummary(result.getPruningActionSummary());
            analysis.setPruningGeneralGuidance(result.getPruningGeneralGuidance());
            analysis.setPruningGuidance(result.getPruningGeneralGuidance());
            analysis.setPropagationInstructions(result.getPropagationInstructions());
            analysis.setHealthDiagnosis(result.getHealthDiagnosis());
            analysis.setGoalSuggestions(result.getGoalSuggestions());
            analysis.setSpeciesOverview(result.getSpeciesOverview());
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
                Path fullPath = Paths.get(localStorageBasePath).toAbsolutePath().normalize()
                        .resolve(image.getStoragePath());
                bytes = Files.readAllBytes(fullPath);
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

    /**
     * Builds a formatted care context string from the most recently completed analysis for this plant,
     * excluding the analysis currently being processed. Used to give continuity on reanalysis.
     */
    private String buildPriorCareContext(Long plantId, Long currentAnalysisId) {
        return analysisRepository
                .findFirstByPlantIdAndAnalysisTypeInOrderByCreatedAtDesc(
                        plantId,
                        List.of(PlantAnalysis.AnalysisType.REGISTRATION, PlantAnalysis.AnalysisType.REANALYSIS))
                .filter(a -> a.getStatus() == PlantAnalysis.AnalysisStatus.COMPLETED
                        && !a.getId().equals(currentAnalysisId))
                .map(a -> {
                    StringBuilder sb = new StringBuilder();
                    appendIfPresent(sb, "Light needs (summary)", a.getLightNeeds());
                    appendIfPresent(sb, "Light (general guidance)", a.getLightGeneralGuidance());
                    appendIfPresent(sb, "Placement (tailored)", a.getPlacementGuidance());
                    appendIfPresent(sb, "Placement (general guidance)", a.getPlacementGeneralGuidance());
                    appendIfPresent(sb, "Watering amount", a.getWateringAmount());
                    appendIfPresent(sb, "Watering frequency", a.getWateringFrequency());
                    appendIfPresent(sb, "Watering guidance", a.getWateringGuidance());
                    appendIfPresent(sb, "Fertilizer type", a.getFertilizerType());
                    appendIfPresent(sb, "Fertilizer frequency", a.getFertilizerFrequency());
                    appendIfPresent(sb, "Fertilizer guidance", a.getFertilizerGuidance());
                    appendIfPresent(sb, "Pruning action summary", a.getPruningActionSummary());
                    appendIfPresent(sb, "Pruning (species guidance)", a.getPruningGeneralGuidance());
                    if ((a.getPruningActionSummary() == null || a.getPruningActionSummary().isBlank())
                            && (a.getPruningGeneralGuidance() == null || a.getPruningGeneralGuidance().isBlank())) {
                        appendIfPresent(sb, "Pruning guidance", a.getPruningGuidance());
                    }
                    appendIfPresent(sb, "Health diagnosis", a.getHealthDiagnosis());
                    appendIfPresent(sb, "Goal suggestions", a.getGoalSuggestions());
                    return sb.toString().trim();
                })
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    /**
     * Builds a formatted care history string from the most recent watering, fertilizer, and prune events.
     */
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
     * Image-only entries are skipped since they cannot be rendered as text context.
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

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }
}
