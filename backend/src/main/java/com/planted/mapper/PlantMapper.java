package com.planted.mapper;

import com.planted.dto.*;
import com.planted.entity.*;
import com.planted.repository.PlantImageRepository;
import com.planted.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
                state.getWeatherCareNote(),
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
                analysis.getLightGeneralGuidance(),
                analysis.getPlacementGuidance(),
                analysis.getPlacementGeneralGuidance(),
                analysis.getWateringGuidance(),
                analysis.getWateringAmount(),
                analysis.getWateringFrequency(),
                analysis.getFertilizerGuidance(),
                analysis.getFertilizerType(),
                analysis.getFertilizerFrequency(),
                analysis.getPruningGuidance(),
                analysis.getPruningActionSummary(),
                analysis.getPruningGeneralGuidance(),
                analysis.getPropagationInstructions(),
                analysis.getHealthDiagnosis(),
                analysis.getGoalSuggestions(),
                resolveSpeciesOverview(analysis),
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
        return new PlantHistoryEntryDto(
                entry.getId(), "JOURNAL", entry.getNoteText(), imageDto, entry.getCreatedAt());
    }

    public PlantHistoryEntryDto toHistoryEntryDto(PlantWateringEvent event) {
        if (event == null) return null;
        String note = event.getNotes() != null && !event.getNotes().isBlank()
                ? "Watered — " + event.getNotes().trim()
                : "Watered";
        return new PlantHistoryEntryDto(event.getId(), "WATERING", note, null, event.getWateredAt());
    }

    public PlantHistoryEntryDto toHistoryEntryDto(PlantFertilizerEvent event) {
        if (event == null) return null;
        StringBuilder line = new StringBuilder("Fertilized");
        if (event.getFertilizerType() != null && !event.getFertilizerType().isBlank()) {
            line.append(" (").append(event.getFertilizerType().trim()).append(")");
        }
        if (event.getNotes() != null && !event.getNotes().isBlank()) {
            line.append(" — ").append(event.getNotes().trim());
        }
        return new PlantHistoryEntryDto(event.getId(), "FERTILIZER", line.toString(), null, event.getFertilizedAt());
    }

    public PlantHistoryEntryDto toHistoryEntryDto(PlantPruneEvent event) {
        if (event == null) return null;
        String note = event.getNotes() != null && !event.getNotes().isBlank()
                ? "Pruned — " + event.getNotes().trim()
                : "Pruned";
        PlantImageDto imageDto = null;
        if (event.getImageId() != null) {
            imageDto = plantImageRepository.findById(event.getImageId())
                    .map(this::toImageDto)
                    .orElse(null);
        }
        return new PlantHistoryEntryDto(event.getId(), "PRUNE", note, imageDto, event.getPrunedAt());
    }

    public List<PlantHistoryEntryDto> mergeHistoryTimeline(
            List<PlantHistoryEntry> journalEntries,
            List<PlantWateringEvent> waterings,
            List<PlantFertilizerEvent> fertilizers,
            List<PlantPruneEvent> prunes) {
        List<PlantHistoryEntryDto> merged = new ArrayList<>();
        if (journalEntries != null) {
            journalEntries.stream().map(this::toHistoryEntryDto).forEach(merged::add);
        }
        if (waterings != null) {
            waterings.stream().map(this::toHistoryEntryDto).forEach(merged::add);
        }
        if (fertilizers != null) {
            fertilizers.stream().map(this::toHistoryEntryDto).forEach(merged::add);
        }
        if (prunes != null) {
            prunes.stream().map(this::toHistoryEntryDto).forEach(merged::add);
        }
        merged.sort(Comparator.comparing(PlantHistoryEntryDto::createdAt).reversed());
        return merged;
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
            List<PlantHistoryEntryDto> historyEntries,
            String historySummaryText,
            List<HistoryDailyDigestDto> historyDailyDigests,
            OffsetDateTime historySummaryCompletedAt,
            String historySummaryError,
            boolean historySummaryEligible) {

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
                plant.getGrowingContext() != null ? plant.getGrowingContext().name() : "INDOOR",
                plant.getLatitude(),
                plant.getLongitude(),
                plant.getStatus().name(),
                toImageDto(illustratedImage),
                originalImages.stream().map(this::toImageDto).toList(),
                healthyReferenceImages.stream().map(this::toImageDto).toList(),
                pruneUpdateImages.stream().map(this::toImageDto).toList(),
                toAnalysisSummaryDto(latestAnalysis),
                toReminderStateDto(reminderState),
                hasActiveJobs,
                historyEntries,
                historySummaryText,
                historyDailyDigests,
                historySummaryCompletedAt,
                historySummaryError,
                historySummaryEligible,
                plant.getCreatedAt(),
                plant.getUpdatedAt()
        );
    }

    /**
     * Prefer persisted encyclopedia-style prose; fall back to legacy bullet list joined as paragraphs.
     */
    public static String resolveSpeciesOverview(PlantAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        String overview = analysis.getSpeciesOverview();
        if (overview != null && !overview.isBlank()) {
            return overview;
        }
        List<String> legacy = analysis.getInterestingFactsJson();
        if (legacy != null && !legacy.isEmpty()) {
            String joined = legacy.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining("\n\n"));
            if (!joined.isBlank()) {
                return joined;
            }
        }
        return syntheticSpeciesOverviewFromStructuredFields(analysis);
    }

    /**
     * When the model never filled species_overview / interesting_facts, stitch a short narrative from
     * normalized taxonomy and care fields so the About pane is not empty for older analyses.
     */
    static String syntheticSpeciesOverviewFromStructuredFields(PlantAnalysis a) {
        List<String> paras = new ArrayList<>();
        if (notBlank(a.getScientificName())) {
            StringBuilder sb = new StringBuilder(a.getScientificName().trim());
            if (notBlank(a.getClassName())) {
                sb.append(" (").append(a.getClassName().trim()).append(")");
            }
            sb.append(".");
            paras.add(sb.toString());
        }
        List<String> regions = a.getNativeRegionsJson();
        if (regions != null && !regions.isEmpty()) {
            String loc = regions.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .collect(Collectors.joining(", "));
            if (!loc.isEmpty()) {
                paras.add("It is native to " + loc + ".");
            }
        }
        if (notBlank(a.getLightNeeds()) || notBlank(a.getPlacementGuidance())) {
            StringBuilder sb = new StringBuilder();
            if (notBlank(a.getLightNeeds())) {
                sb.append(a.getLightNeeds().trim());
            }
            if (notBlank(a.getPlacementGuidance())) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(a.getPlacementGuidance().trim());
            }
            paras.add(sb.toString());
        }
        if (paras.isEmpty()) {
            return null;
        }
        return String.join("\n\n", paras);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String buildSpeciesLabel(String genus, String species) {
        if (genus == null && species == null) return "Unknown Plant";
        if (species == null) return genus;
        if (genus == null) return species;
        return genus + " " + species;
    }
}
