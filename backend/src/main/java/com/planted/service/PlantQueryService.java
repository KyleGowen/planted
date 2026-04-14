package com.planted.service;

import com.planted.dto.PlantDetailResponse;
import com.planted.dto.PlantListItemResponse;
import com.planted.entity.*;
import com.planted.mapper.PlantMapper;
import com.planted.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
    private final PlantMapper plantMapper;

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

                    String analysisStatus = analysisRepository
                            .findFirstByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(
                                    plant.getId(), PlantAnalysis.AnalysisType.REGISTRATION)
                            .map(a -> a.getStatus().name())
                            .orElse(null);

                    return plantMapper.toListItemResponse(plant, illustratedImage, originalImage, reminderState, analysisStatus);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PlantDetailResponse getPlantDetail(Long plantId) {
        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));

        PlantImage illustratedImage = imageRepository
                .findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(plantId, PlantImage.ImageType.ILLUSTRATED)
                .orElse(null);

        List<PlantImage> originalImages = imageRepository
                .findByPlantIdAndImageTypeOrderBySortOrderAscCreatedAtAsc(plantId, PlantImage.ImageType.ORIGINAL_UPLOAD);

        List<PlantImage> healthyReferenceImages = imageRepository
                .findByPlantIdAndImageTypeOrderBySortOrderAscCreatedAtAsc(plantId, PlantImage.ImageType.HEALTHY_REFERENCE);

        List<PlantImage> pruneUpdateImages = imageRepository
                .findByPlantIdAndImageTypeOrderBySortOrderAscCreatedAtAsc(plantId, PlantImage.ImageType.PRUNE_UPDATE);

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

        List<PlantHistoryEntry> historyEntries = historyEntryRepository
                .findByPlantIdOrderByCreatedAtDesc(plantId);

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

        return plantMapper.toDetailResponse(
                plant,
                illustratedImage,
                originalImages,
                healthyReferenceImages,
                pruneUpdateImages,
                latestAnalysis,
                reminderState,
                hasActiveJobs,
                historyEntries,
                historySummaryText,
                historySummaryCompletedAt,
                historySummaryError,
                historySummaryEligible
        );
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
