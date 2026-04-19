package com.planted.mapper;

import com.planted.dto.*;
import com.planted.entity.*;
import com.planted.repository.PlantImageRepository;
import com.planted.storage.ImageStorageService;
import com.planted.util.TaxonomicDisplayFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                state.isLightAttentionNeeded(),
                state.isPlacementAttentionNeeded(),
                state.getNextWateringInstruction(),
                state.getNextFertilizerInstruction(),
                state.getNextPruningInstruction(),
                state.getHealthAttentionReason(),
                state.getLightAttentionReason(),
                state.getPlacementAttentionReason(),
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
                analysis.getTaxonomicFamily(),
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
            PlantAnalysis registrationAnalysis,
            PlantBioSection speciesDescriptionSection) {

        String taxonFallback = TaxonomicDisplayFormatter.formatLine(
                plant.getTaxonomicFamily(), plant.getGenus(), plant.getSpecies(), plant.getVariety());
        if (taxonFallback == null) {
            taxonFallback = TaxonomicDisplayFormatter.formatBinomial(plant.getGenus(), plant.getSpecies());
        }
        String displayLabel = plant.getName() != null
                ? plant.getName()
                : plant.getSpeciesLabel() != null ? plant.getSpeciesLabel()
                : taxonFallback != null ? taxonFallback : "Unknown Plant";

        String analysisStatus = registrationAnalysis != null
                ? registrationAnalysis.getStatus().name()
                : null;
        String scientificName = registrationAnalysis != null
                ? registrationAnalysis.getScientificName()
                : null;
        String speciesOverview = resolveSpeciesOverviewFromBioOrLegacy(
                speciesDescriptionSection, registrationAnalysis);

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
                analysisStatus,
                scientificName,
                speciesOverview,
                plant.getGrowingContext()
        );
    }

    /**
     * Overload retained for callers that haven't been updated to pass the
     * {@link PlantBioSection} row. Equivalent to calling the main method with
     * {@code speciesDescriptionSection = null}, which falls back to the legacy
     * analysis.
     */
    public PlantListItemResponse toListItemResponse(
            Plant plant,
            PlantImage illustratedImage,
            PlantImage originalImage,
            PlantReminderState reminderState,
            PlantAnalysis registrationAnalysis) {
        return toListItemResponse(plant, illustratedImage, originalImage, reminderState,
                registrationAnalysis, null);
    }

    /**
     * Prefer the decomposed SPECIES_DESCRIPTION bio section; fall back to the legacy
     * monolithic analysis so plants registered before the decomposition still render.
     */
    private static String resolveSpeciesOverviewFromBioOrLegacy(
            PlantBioSection section, PlantAnalysis legacy) {
        if (section != null && section.getContentJsonb() != null) {
            Object overview = section.getContentJsonb().get("overview");
            if (overview instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return resolveSpeciesOverview(legacy);
    }

    public PlantDetailResponse toDetailResponse(
            Plant plant,
            PlantImage illustratedImage,
            List<PlantImage> originalImages,
            List<PlantImage> healthyReferenceImages,
            List<PlantImage> pruneUpdateImages,
            PlantAnalysis latestAnalysis,
            List<PlantBioSection> bioSections,
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
                plant.getPlacementNotesSummary(),
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
                toBioSectionsMap(bioSections, latestAnalysis),
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
     * Build the {@code bioSections} map, backfilling missing sections from {@code latestAnalysis}
     * (the legacy monolithic analysis) so existing plants render correctly before the async
     * refresh jobs have populated the cache table.
     */
    public Map<String, BioSectionDto> toBioSectionsMap(
            List<PlantBioSection> bioSections,
            PlantAnalysis legacyFallback) {
        Map<String, BioSectionDto> out = new LinkedHashMap<>();
        if (bioSections != null) {
            for (PlantBioSection row : bioSections) {
                if (row.getSectionKey() == null) continue;
                out.put(row.getSectionKey().name(), toBioSectionDto(row));
            }
        }
        for (PlantBioSectionKey key : PlantBioSectionKey.values()) {
            out.computeIfAbsent(key.name(), k -> legacyFallbackSection(key, legacyFallback));
        }
        return out;
    }

    private BioSectionDto toBioSectionDto(PlantBioSection row) {
        boolean refreshing = row.getStatus() == PlantBioSection.Status.PENDING
                || row.getStatus() == PlantBioSection.Status.PROCESSING
                || row.getGeneratedAt() == null
                || row.getGeneratedAt().plus(row.getSectionKey().ttl()).isBefore(OffsetDateTime.now());
        return new BioSectionDto(
                row.getSectionKey().name(),
                row.getStatus() != null ? row.getStatus().name() : "PENDING",
                row.getContentJsonb(),
                row.getPromptKey(),
                row.getPromptVersion(),
                row.getGeneratedAt(),
                refreshing,
                row.getLastError()
        );
    }

    /** When a bio section has not been produced yet, mirror what's available in the legacy analysis so the UI has something to show. */
    private BioSectionDto legacyFallbackSection(PlantBioSectionKey key, PlantAnalysis a) {
        if (a == null) {
            return new BioSectionDto(key.name(), "PENDING", null, null, null, null, true, null);
        }
        Map<String, Object> content = switch (key) {
            case SPECIES_ID -> legacySpeciesId(a);
            case HEALTH_ASSESSMENT -> legacyHealthAssessment(a);
            case SPECIES_DESCRIPTION -> legacySpeciesDescription(a);
            case WATER_CARE -> legacyWaterCare(a);
            case FERTILIZER_CARE -> legacyFertilizerCare(a);
            case PRUNING_CARE -> legacyPruningCare(a);
            case LIGHT_CARE -> legacyLightCare(a);
            case PLACEMENT_CARE -> legacyPlacementCare(a);
            case HISTORY_SUMMARY -> null;
        };
        if (content == null || content.values().stream().allMatch(v -> v == null || (v instanceof String s && s.isBlank()))) {
            return new BioSectionDto(key.name(), "PENDING", null, null, null, null, true, null);
        }
        return new BioSectionDto(key.name(), "COMPLETED", content, "legacy:plant_registration_analysis_v1",
                null, a.getCompletedAt(), true, null);
    }

    private static Map<String, Object> legacySpeciesId(PlantAnalysis a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("className", a.getClassName());
        m.put("taxonomicFamily", a.getTaxonomicFamily());
        m.put("genus", a.getGenus());
        m.put("species", a.getSpecies());
        m.put("variety", a.getVariety());
        m.put("confidence", a.getConfidence());
        m.put("nativeRegions", a.getNativeRegionsJson());
        return m;
    }

    private static Map<String, Object> legacyHealthAssessment(PlantAnalysis a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("diagnosis", a.getHealthDiagnosis());
        m.put("severity", null);
        m.put("signs", List.of());
        m.put("checks", List.of());
        return m;
    }

    private static Map<String, Object> legacySpeciesDescription(PlantAnalysis a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("overview", resolveSpeciesOverview(a));
        m.put("uses", a.getUsesJson() != null ? a.getUsesJson() : List.of());
        return m;
    }

    private static Map<String, Object> legacyWaterCare(PlantAnalysis a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", a.getWateringAmount());
        m.put("frequency", a.getWateringFrequency());
        m.put("guidance", a.getWateringGuidance());
        return m;
    }

    private static Map<String, Object> legacyFertilizerCare(PlantAnalysis a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", a.getFertilizerType());
        m.put("frequency", a.getFertilizerFrequency());
        m.put("guidance", a.getFertilizerGuidance());
        return m;
    }

    private static Map<String, Object> legacyPruningCare(PlantAnalysis a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("actionSummary", a.getPruningActionSummary());
        m.put("guidance", a.getPruningGuidance());
        m.put("generalGuidance", a.getPruningGeneralGuidance());
        return m;
    }

    private static Map<String, Object> legacyLightCare(PlantAnalysis a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("needs", a.getLightNeeds());
        m.put("generalGuidance", a.getLightGeneralGuidance());
        return m;
    }

    private static Map<String, Object> legacyPlacementCare(PlantAnalysis a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("guidance", a.getPlacementGuidance());
        m.put("generalGuidance", a.getPlacementGeneralGuidance());
        return m;
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
}
