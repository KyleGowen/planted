package com.planted.mapper;

import com.planted.dto.*;
import com.planted.entity.*;
import com.planted.repository.PlantImageRepository;
import com.planted.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PlantMapper {

    private final ImageStorageService imageStorageService;
    private final PlantImageRepository plantImageRepository;

    public PlantImageDto toImageDto(PlantImage image) {
        if (image == null) return null;
        return new PlantImageDto(
                image.getId(),
                image.getImageType().name(),
                imageStorageService.getUrl(image.getStoragePath(), image.getStorageType()),
                image.getMimeType(),
                image.getCapturedAt(),
                image.getSortOrder()
        );
    }

    public ReminderStateDto toReminderStateDto(PlantReminderState state) {
        if (state == null) return null;
        return new ReminderStateDto(
                state.isWateringDue(),
                state.isWateringOverdue(),
                state.isFertilizerDue(),
                state.isPruningDue(),
                state.isHealthAttentionNeeded(),
                state.isGoalAttentionNeeded(),
                state.getNextWateringInstruction(),
                state.getNextFertilizerInstruction(),
                state.getNextPruningInstruction(),
                state.getLastComputedAt()
        );
    }

    public AnalysisSummaryDto toAnalysisSummaryDto(PlantAnalysis analysis) {
        if (analysis == null) return null;
        return new AnalysisSummaryDto(
                analysis.getId(),
                analysis.getAnalysisType().name(),
                analysis.getStatus().name(),
                analysis.getGenus(),
                analysis.getSpecies(),
                analysis.getVariety(),
                analysis.getScientificName(),
                analysis.getConfidence(),
                analysis.getNativeRegionsJson(),
                analysis.getLightNeeds(),
                analysis.getPlacementGuidance(),
                analysis.getWateringGuidance(),
                analysis.getWateringAmount(),
                analysis.getWateringFrequency(),
                analysis.getFertilizerGuidance(),
                analysis.getFertilizerType(),
                analysis.getFertilizerFrequency(),
                analysis.getPruningGuidance(),
                analysis.getPropagationInstructions(),
                analysis.getHealthDiagnosis(),
                analysis.getGoalSuggestions(),
                analysis.getInterestingFactsJson(),
                analysis.getUsesJson(),
                analysis.getCompletedAt(),
                analysis.getFailureReason()
        );
    }

    public PlantHistoryEntryDto toHistoryEntryDto(PlantHistoryEntry entry) {
        if (entry == null) return null;
        PlantImageDto imageDto = null;
        if (entry.getImageId() != null) {
            imageDto = plantImageRepository.findById(entry.getImageId())
                    .map(this::toImageDto)
                    .orElse(null);
        }
        return new PlantHistoryEntryDto(entry.getId(), entry.getNoteText(), imageDto, entry.getCreatedAt());
    }

    public PlantListItemResponse toListItemResponse(
            Plant plant,
            PlantImage illustratedImage,
            PlantImage originalImage,
            PlantReminderState reminderState,
            String analysisStatus) {

        String displayLabel = plant.getName() != null
                ? plant.getName()
                : plant.getSpeciesLabel() != null ? plant.getSpeciesLabel()
                : buildSpeciesLabel(plant.getGenus(), plant.getSpecies());

        return new PlantListItemResponse(
                plant.getId(),
                plant.getName(),
                plant.getGenus(),
                plant.getSpecies(),
                plant.getSpeciesLabel(),
                displayLabel,
                toImageDto(illustratedImage),
                toImageDto(originalImage),
                toReminderStateDto(reminderState),
                plant.getStatus().name(),
                analysisStatus
        );
    }

    public PlantDetailResponse toDetailResponse(
            Plant plant,
            PlantImage illustratedImage,
            List<PlantImage> originalImages,
            List<PlantImage> healthyReferenceImages,
            List<PlantImage> pruneUpdateImages,
            PlantAnalysis latestAnalysis,
            PlantReminderState reminderState,
            boolean hasActiveJobs,
            List<PlantHistoryEntry> historyEntries) {

        return new PlantDetailResponse(
                plant.getId(),
                plant.getName(),
                plant.getGenus(),
                plant.getSpecies(),
                plant.getVariety(),
                plant.getSpeciesLabel(),
                plant.getLocation(),
                plant.getGoalsText(),
                plant.getGeoCountry(),
                plant.getGeoState(),
                plant.getGeoCity(),
                plant.getStatus().name(),
                toImageDto(illustratedImage),
                originalImages.stream().map(this::toImageDto).toList(),
                healthyReferenceImages.stream().map(this::toImageDto).toList(),
                pruneUpdateImages.stream().map(this::toImageDto).toList(),
                toAnalysisSummaryDto(latestAnalysis),
                toReminderStateDto(reminderState),
                hasActiveJobs,
                historyEntries.stream().map(this::toHistoryEntryDto).toList(),
                plant.getCreatedAt(),
                plant.getUpdatedAt()
        );
    }

    private String buildSpeciesLabel(String genus, String species) {
        if (genus == null && species == null) return "Unknown Plant";
        if (species == null) return genus;
        if (genus == null) return species;
        return genus + " " + species;
    }
}
