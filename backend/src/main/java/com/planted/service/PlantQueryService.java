package com.planted.service;

import com.planted.dto.PlantDetailResponse;
import com.planted.dto.PlantListItemResponse;
import com.planted.entity.*;
import com.planted.mapper.PlantMapper;
import com.planted.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlantQueryService {

    private final PlantRepository plantRepository;
    private final PlantImageRepository imageRepository;
    private final PlantAnalysisRepository analysisRepository;
    private final PlantReminderStateRepository reminderStateRepository;
    private final PlantMapper plantMapper;

    @Transactional(readOnly = true)
    public List<PlantListItemResponse> listActivePlants() {
        return plantRepository.findAllActive().stream()
                .map(plant -> {
                    PlantImage illustratedImage = imageRepository
                            .findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(
                                    plant.getId(), PlantImage.ImageType.ILLUSTRATED)
                            .orElse(null);

                    PlantReminderState reminderState = reminderStateRepository
                            .findByPlantId(plant.getId())
                            .orElse(null);

                    String analysisStatus = analysisRepository
                            .findFirstByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(
                                    plant.getId(), PlantAnalysis.AnalysisType.REGISTRATION)
                            .map(a -> a.getStatus().name())
                            .orElse(null);

                    return plantMapper.toListItemResponse(plant, illustratedImage, reminderState, analysisStatus);
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

        PlantAnalysis latestAnalysis = analysisRepository
                .findFirstByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(plantId, PlantAnalysis.AnalysisType.REGISTRATION)
                .orElse(null);

        PlantReminderState reminderState = reminderStateRepository
                .findByPlantId(plantId)
                .orElse(null);

        boolean hasActiveJobs = analysisRepository
                .findByPlantIdAndStatusIn(plantId, List.of(
                        PlantAnalysis.AnalysisStatus.PENDING,
                        PlantAnalysis.AnalysisStatus.PROCESSING))
                .stream().anyMatch(_ -> true);

        return plantMapper.toDetailResponse(
                plant,
                illustratedImage,
                originalImages,
                healthyReferenceImages,
                pruneUpdateImages,
                latestAnalysis,
                reminderState,
                hasActiveJobs
        );
    }
}
