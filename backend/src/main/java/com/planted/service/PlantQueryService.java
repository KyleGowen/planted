package com.planted.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planted.dto.ActivityEntryDto;
import com.planted.dto.HistoryDailyDigestDto;
import com.planted.dto.PlantDetailResponse;
import com.planted.dto.PlantHistoryEntryDto;
import com.planted.dto.PlantImageDto;
import com.planted.dto.PlantListItemResponse;
import com.planted.entity.*;
import com.planted.mapper.PlantMapper;
import com.planted.queue.PlantJobMessage;
import com.planted.queue.PlantJobPublisher;
import com.planted.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlantQueryService {

    private final PlantRepository plantRepository;
    private final PlantImageRepository imageRepository;
    private final PlantAnalysisRepository analysisRepository;
    private final PlantReminderStateRepository reminderStateRepository;
    private final PlantHistoryEntryRepository historyEntryRepository;
    private final PlantWateringEventRepository wateringEventRepository;
    private final PlantFertilizerEventRepository fertilizerEventRepository;
    private final PlantPruneEventRepository pruneEventRepository;
    private final PlantBioSectionRepository bioSectionRepository;
    private final PlantJobPublisher jobPublisher;
    private final PlantMapper plantMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<PlantListItemResponse> listActivePlants() {
        return plantRepository.findAllActive().stream()
                .map(plant -> {
                    PlantImage illustratedImage = imageRepository
                            .findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(
                                    plant.getId(), PlantImage.ImageType.ILLUSTRATED)
                            .orElse(null);

                    PlantImage originalImage = imageRepository
                            .findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(
                                    plant.getId(), PlantImage.ImageType.ORIGINAL_UPLOAD)
                            .orElse(null);

                    PlantReminderState reminderState = reminderStateRepository
                            .findByPlantId(plant.getId())
                            .orElse(null);

                    PlantAnalysis registrationAnalysis = analysisRepository
                            .findFirstByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(
                                    plant.getId(), PlantAnalysis.AnalysisType.REGISTRATION)
                            .orElse(null);

                    // Prefer the decomposed SPECIES_DESCRIPTION bio section; the mapper
                    // falls back to the legacy analysis when this is null.
                    PlantBioSection descriptionSection = bioSectionRepository
                            .findByPlantIdAndSectionKey(plant.getId(), PlantBioSectionKey.SPECIES_DESCRIPTION)
                            .orElse(null);

                    return plantMapper.toListItemResponse(
                            plant, illustratedImage, originalImage, reminderState,
                            registrationAnalysis, descriptionSection);
                })
                .toList();
    }

    /**
     * Aggregates the most recent care events and journal entries across all active plants,
     * suitable for the mobile activity feed.
     *
     * <p>Fetches the top 25 rows from each event source, merges, sorts newest-first,
     * and returns up to {@code limit} entries enriched with plant name and thumbnail.
     */
    @Transactional(readOnly = true)
    public List<ActivityEntryDto> listRecentActivity(int limit) {
        List<Plant> plants = plantRepository.findAllActive();
        if (plants.isEmpty()) return List.of();

        Map<Long, Plant> plantById = new HashMap<>();
        Map<Long, PlantImageDto> thumbnailById = new HashMap<>();

        for (Plant plant : plants) {
            plantById.put(plant.getId(), plant);
            PlantImage thumb = imageRepository
                    .findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(plant.getId(), PlantImage.ImageType.ILLUSTRATED)
                    .orElseGet(() -> imageRepository
                            .findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(
                                    plant.getId(), PlantImage.ImageType.ORIGINAL_UPLOAD)
                            .orElse(null));
            if (thumb != null) {
                thumbnailById.put(plant.getId(), plantMapper.toImageDto(thumb));
            }
        }

        int perSource = Math.max(limit, 25);
        Pageable page = PageRequest.of(0, perSource);

        List<ActivityEntryDto> entries = new ArrayList<>();

        for (PlantHistoryEntry e : historyEntryRepository.findRecentAcrossAllPlants(page)) {
            Plant plant = plantById.get(e.getPlantId());
            if (plant == null) continue;
            PlantImageDto image = e.getImageId() != null
                    ? imageRepository.findById(e.getImageId()).map(plantMapper::toImageDto).orElse(null)
                    : null;
            entries.add(new ActivityEntryDto(
                    plant.getId(), resolveActivityPlantName(plant),
                    thumbnailById.get(plant.getId()),
                    "JOURNAL", e.getNoteText(), image, e.getCreatedAt()));
        }

        for (PlantWateringEvent e : wateringEventRepository.findRecentAcrossAllPlants(page)) {
            Plant plant = plantById.get(e.getPlantId());
            if (plant == null) continue;
            String note = e.getNotes() != null && !e.getNotes().isBlank()
                    ? "Watered — " + e.getNotes().trim() : "Watered";
            entries.add(new ActivityEntryDto(
                    plant.getId(), resolveActivityPlantName(plant),
                    thumbnailById.get(plant.getId()),
                    "WATERING", note, null, e.getWateredAt()));
        }

        for (PlantFertilizerEvent e : fertilizerEventRepository.findRecentAcrossAllPlants(page)) {
            Plant plant = plantById.get(e.getPlantId());
            if (plant == null) continue;
            StringBuilder note = new StringBuilder("Fertilized");
            if (e.getFertilizerType() != null && !e.getFertilizerType().isBlank()) {
                note.append(" (").append(e.getFertilizerType().trim()).append(")");
            }
            if (e.getNotes() != null && !e.getNotes().isBlank()) {
                note.append(" — ").append(e.getNotes().trim());
            }
            entries.add(new ActivityEntryDto(
                    plant.getId(), resolveActivityPlantName(plant),
                    thumbnailById.get(plant.getId()),
                    "FERTILIZER", note.toString(), null, e.getFertilizedAt()));
        }

        for (PlantPruneEvent e : pruneEventRepository.findRecentAcrossAllPlants(page)) {
            Plant plant = plantById.get(e.getPlantId());
            if (plant == null) continue;
            String note = e.getNotes() != null && !e.getNotes().isBlank()
                    ? "Pruned — " + e.getNotes().trim() : "Pruned";
            PlantImageDto image = e.getImageId() != null
                    ? imageRepository.findById(e.getImageId()).map(plantMapper::toImageDto).orElse(null)
                    : null;
            entries.add(new ActivityEntryDto(
                    plant.getId(), resolveActivityPlantName(plant),
                    thumbnailById.get(plant.getId()),
                    "PRUNE", note, image, e.getPrunedAt()));
        }

        entries.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return entries.stream().limit(limit).toList();
    }

    private String resolveActivityPlantName(Plant plant) {
        if (plant.getName() != null && !plant.getName().isBlank()) return plant.getName();
        if (plant.getGenus() != null && !plant.getGenus().isBlank()) {
            return plant.getSpecies() != null && !plant.getSpecies().isBlank()
                    ? plant.getGenus() + " " + plant.getSpecies()
                    : plant.getGenus();
        }
        return "Unknown plant";
    }

    @Transactional(readOnly = true)
    public PlantDetailResponse getPlantDetail(Long plantId) {
        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));

        PlantImage illustratedImage = imageRepository
                .findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(plantId, PlantImage.ImageType.ILLUSTRATED)
                .orElse(null);

        // Newest-first so the detail hero (originalImages[0]) is always the most recent upload,
        // and older photos flow into the "Photo history" strip in reverse chronological order.
        // Matches the list-thumbnail ordering (findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc).
        List<PlantImage> originalImages = imageRepository
                .findByPlantIdAndImageTypeOrderByCreatedAtDesc(plantId, PlantImage.ImageType.ORIGINAL_UPLOAD);

        List<PlantImage> healthyReferenceImages = imageRepository
                .findByPlantIdAndImageTypeOrderBySortOrderAscCreatedAtAsc(plantId, PlantImage.ImageType.HEALTHY_REFERENCE);

        List<PlantImage> pruneUpdateImages = imageRepository
                .findByPlantIdAndImageTypeOrderByCreatedAtDesc(plantId, PlantImage.ImageType.PRUNE_UPDATE);

        List<PlantAnalysis> latestCareRows = analysisRepository.findByPlantIdAndAnalysisTypeInOrderByCreatedAtDesc(
                plantId,
                List.of(PlantAnalysis.AnalysisType.REGISTRATION, PlantAnalysis.AnalysisType.REANALYSIS),
                PageRequest.of(0, 1));
        PlantAnalysis latestAnalysis = latestCareRows.isEmpty() ? null : latestCareRows.get(0);

        PlantReminderState reminderState = reminderStateRepository
                .findByPlantId(plantId)
                .orElse(null);

        boolean hasActiveJobs = !analysisRepository
                .findByPlantIdAndStatusIn(plantId, List.of(
                        PlantAnalysis.AnalysisStatus.PENDING,
                        PlantAnalysis.AnalysisStatus.PROCESSING))
                .isEmpty();

        List<PlantHistoryEntry> journalEntries = historyEntryRepository
                .findByPlantIdOrderByCreatedAtDesc(plantId);
        List<PlantWateringEvent> waterings = wateringEventRepository.findByPlantIdOrderByWateredAtDesc(plantId);
        List<PlantFertilizerEvent> fertilizers = fertilizerEventRepository.findByPlantIdOrderByFertilizedAtDesc(plantId);
        List<PlantPruneEvent> prunes = pruneEventRepository.findByPlantIdOrderByPrunedAtDesc(plantId);
        List<PlantHistoryEntryDto> historyTimeline = plantMapper.mergeHistoryTimeline(
                journalEntries, waterings, fertilizers, prunes);

        List<PlantAnalysis> summaryCandidates = analysisRepository.findLatestCompletedHistorySummaryWithBody(
                plantId,
                PlantAnalysis.AnalysisType.INFO_PANEL,
                PlantAnalysis.AnalysisStatus.COMPLETED,
                PageRequest.of(0, 1));
        PlantAnalysis latestHistorySummary = summaryCandidates.isEmpty() ? null : summaryCandidates.get(0);
        String historySummaryText = latestHistorySummary != null ? latestHistorySummary.getInfoPanelSummary() : null;
        OffsetDateTime historySummaryCompletedAt =
                latestHistorySummary != null ? latestHistorySummary.getCompletedAt() : null;

        String historySummaryError = null;
        List<PlantAnalysis> newestInfoPanelRows = analysisRepository.findByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(
                plantId, PlantAnalysis.AnalysisType.INFO_PANEL, PageRequest.of(0, 1));
        Optional<PlantAnalysis> newestInfoPanel =
                newestInfoPanelRows.isEmpty() ? Optional.empty() : Optional.of(newestInfoPanelRows.get(0));
        if (newestInfoPanel.isPresent()
                && newestInfoPanel.get().getStatus() == PlantAnalysis.AnalysisStatus.FAILED
                && latestHistorySummary == null) {
            String reason = newestInfoPanel.get().getFailureReason();
            historySummaryError = (reason != null && !reason.isBlank())
                    ? reason
                    : "Summary generation failed. Check OpenAI configuration and try again.";
        }

        boolean historySummaryEligible = hasHistorySummarySourceData(plantId);

        List<HistoryDailyDigestDto> historyDailyDigests = extractHistoryDailyDigests(latestHistorySummary);

        List<PlantBioSection> bioSections = bioSectionRepository.findByPlantId(plantId);
        enqueueRefreshesForStaleSections(plantId, bioSections);

        return plantMapper.toDetailResponse(
                plant,
                illustratedImage,
                originalImages,
                healthyReferenceImages,
                pruneUpdateImages,
                latestAnalysis,
                bioSections,
                reminderState,
                hasActiveJobs,
                historyTimeline,
                historySummaryText,
                historyDailyDigests,
                historySummaryCompletedAt,
                historySummaryError,
                historySummaryEligible
        );
    }

    /**
     * Lazy refresh: when a plant is viewed, any section whose cached value is stale (null
     * generated_at, expired TTL, or missing entirely) enqueues a background refresh job.
     * The user still sees whatever cached value is available in the meantime. Never calls
     * OpenAI directly on the read path.
     *
     * <p>HISTORY_SUMMARY is excluded unless the plant actually has timeline source data —
     * otherwise every empty plant would burn a job on an empty prompt. Similarly,
     * species-dependent sections are only enqueued once SPECIES_ID is COMPLETED.
     */
    private void enqueueRefreshesForStaleSections(Long plantId, List<PlantBioSection> existing) {
        OffsetDateTime now = OffsetDateTime.now();
        Set<PlantBioSectionKey> have = new HashSet<>();
        boolean speciesResolved = false;
        for (PlantBioSection row : existing) {
            have.add(row.getSectionKey());
            if (row.getSectionKey() == PlantBioSectionKey.SPECIES_ID
                    && row.getStatus() == PlantBioSection.Status.COMPLETED) {
                speciesResolved = true;
            }
        }

        for (PlantBioSection row : existing) {
            PlantBioSectionKey key = row.getSectionKey();
            if (row.getStatus() == PlantBioSection.Status.PROCESSING) continue;
            boolean stale = row.getGeneratedAt() == null
                    || row.getGeneratedAt().plus(key.ttl()).isBefore(now)
                    || row.getStatus() == PlantBioSection.Status.FAILED;
            if (!stale) continue;
            if (!shouldEnqueueOnRead(plantId, key, speciesResolved)) continue;
            enqueueSectionRefresh(plantId, key);
        }

        // Missing-entirely rows — only populate SPECIES_ID + HEALTH_ASSESSMENT here; the
        // species cascade populates the rest to avoid racing before the species name exists.
        for (PlantBioSectionKey key : List.of(PlantBioSectionKey.SPECIES_ID, PlantBioSectionKey.HEALTH_ASSESSMENT)) {
            if (!have.contains(key)) {
                enqueueSectionRefresh(plantId, key);
            }
        }
    }

    private boolean shouldEnqueueOnRead(Long plantId, PlantBioSectionKey key, boolean speciesResolved) {
        if (key == PlantBioSectionKey.HISTORY_SUMMARY) {
            return hasHistorySummarySourceData(plantId);
        }
        if (key == PlantBioSectionKey.SPECIES_ID || key == PlantBioSectionKey.HEALTH_ASSESSMENT) {
            return true;
        }
        // Text sections depend on a resolved species.
        return speciesResolved;
    }

    private void enqueueSectionRefresh(Long plantId, PlantBioSectionKey key) {
        log.info("Lazy refresh: enqueueing {} for plant {}", key, plantId);
        jobPublisher.publish(PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_BIO_SECTION_REFRESH)
                .plantId(plantId)
                .sectionKey(key)
                .build());
    }

    private List<HistoryDailyDigestDto> extractHistoryDailyDigests(PlantAnalysis latestHistorySummary) {
        if (latestHistorySummary == null || latestHistorySummary.getRawModelResponseJsonb() == null) {
            return List.of();
        }
        Object raw = latestHistorySummary.getRawModelResponseJsonb().get("dailyDigests");
        if (raw == null) {
            return List.of();
        }
        try {
            List<HistoryDailyDigestDto> list = objectMapper.convertValue(
                    raw, new TypeReference<List<HistoryDailyDigestDto>>() {});
            return list != null ? Collections.unmodifiableList(list) : List.of();
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse dailyDigests from history summary raw json: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean hasHistorySummarySourceData(Long plantId) {
        if (historyEntryRepository.countByPlantId(plantId) > 0) {
            return true;
        }
        return wateringEventRepository.existsByPlantId(plantId)
                || fertilizerEventRepository.existsByPlantId(plantId)
                || pruneEventRepository.existsByPlantId(plantId);
    }
}
