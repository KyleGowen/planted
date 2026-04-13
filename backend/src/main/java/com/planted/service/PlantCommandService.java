package com.planted.service;

import com.planted.dto.*;
import com.planted.entity.*;
import com.planted.queue.PlantJobMessage;
import com.planted.queue.PlantJobPublisher;
import com.planted.repository.*;
import com.planted.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlantCommandService {

    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic"
    );

    private final PlantRepository plantRepository;
    private final PlantImageRepository plantImageRepository;
    private final PlantAnalysisRepository plantAnalysisRepository;
    private final PlantWateringEventRepository wateringEventRepository;
    private final PlantFertilizerEventRepository fertilizerEventRepository;
    private final PlantPruneEventRepository pruneEventRepository;
    private final PlantReminderStateRepository reminderStateRepository;
    private final PlantHistoryEntryRepository historyEntryRepository;
    private final ImageStorageService imageStorageService;
    private final PlantJobPublisher jobPublisher;

    @Transactional
    public CreatePlantResponse registerPlant(
            MultipartFile imageFile,
            String name,
            String location,
            String goalsText,
            OffsetDateTime lastWateredAt,
            String geoCountry,
            String geoState,
            String geoCity) {

        validateImageFile(imageFile);

        // Create plant record
        Plant plant = Plant.builder()
                .name(name)
                .location(location)
                .goalsText(goalsText)
                .geoCountry(geoCountry)
                .geoState(geoState)
                .geoCity(geoCity)
                .status(PlantStatus.ACTIVE)
                .build();
        plant = plantRepository.save(plant);

        // Store image
        String storagePath = imageStorageService.store(imageFile, plant.getId());
        PlantImage image = PlantImage.builder()
                .plantId(plant.getId())
                .imageType(PlantImage.ImageType.ORIGINAL_UPLOAD)
                .storageType(imageStorageService.getStorageType())
                .storagePath(storagePath)
                .mimeType(imageFile.getContentType())
                .originalFilename(imageFile.getOriginalFilename())
                .capturedAt(OffsetDateTime.now())
                .sortOrder(0)
                .build();
        image = plantImageRepository.save(image);

        // Set primary image
        plant.setPrimaryImageId(image.getId());
        plantRepository.save(plant);

        // Record initial watering event if provided
        if (lastWateredAt != null) {
            PlantWateringEvent wateringEvent = PlantWateringEvent.builder()
                    .plantId(plant.getId())
                    .wateredAt(lastWateredAt)
                    .notes("Initial watering date at registration")
                    .build();
            wateringEventRepository.save(wateringEvent);
        }

        // Create analysis record with PENDING status
        PlantAnalysis analysis = PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.PENDING)
                .build();
        analysis = plantAnalysisRepository.save(analysis);

        // Initialize reminder state
        PlantReminderState reminderState = PlantReminderState.builder()
                .plantId(plant.getId())
                .build();
        reminderStateRepository.save(reminderState);

        // Enqueue background jobs — never block the user on LLM work
        final Long plantId = plant.getId();
        final Long analysisId = analysis.getId();
        final Long imageId = image.getId();

        jobPublisher.publish(PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_REGISTRATION_ANALYSIS)
                .plantId(plantId)
                .analysisId(analysisId)
                .imageIds(List.of(imageId))
                .build());

        jobPublisher.publish(PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_ILLUSTRATION_GENERATION)
                .plantId(plantId)
                .imageIds(List.of(imageId))
                .build());

        log.info("Plant registered: id={}, analysis enqueued: id={}", plantId, analysisId);

        return new CreatePlantResponse(plantId, PlantStatus.ACTIVE.name(), plant.getCreatedAt(), analysisId);
    }

    @Transactional
    public void recordWatering(Long plantId, RecordWateringRequest request) {
        Plant plant = findActivePlant(plantId);
        PlantWateringEvent event = PlantWateringEvent.builder()
                .plantId(plantId)
                .wateredAt(request.wateredAt())
                .notes(request.notes())
                .build();
        wateringEventRepository.save(event);
        log.info("Watering recorded for plant {}", plantId);

        jobPublisher.publish(PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_REMINDER_RECOMPUTE)
                .plantId(plantId)
                .build());
    }

    @Transactional
    public void recordFertilizer(Long plantId, RecordFertilizerRequest request) {
        findActivePlant(plantId);
        PlantFertilizerEvent event = PlantFertilizerEvent.builder()
                .plantId(plantId)
                .fertilizedAt(request.fertilizedAt())
                .fertilizerType(request.fertilizerType())
                .notes(request.notes())
                .build();
        fertilizerEventRepository.save(event);
        log.info("Fertilizer recorded for plant {}", plantId);

        jobPublisher.publish(PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_REMINDER_RECOMPUTE)
                .plantId(plantId)
                .build());
    }

    @Transactional
    public void recordPrune(Long plantId, RecordPruneRequest request, MultipartFile imageFile) {
        findActivePlant(plantId);

        Long imageId = null;
        if (imageFile != null && !imageFile.isEmpty()) {
            validateImageFile(imageFile);
            String storagePath = imageStorageService.store(imageFile, plantId);
            PlantImage pruneImage = PlantImage.builder()
                    .plantId(plantId)
                    .imageType(PlantImage.ImageType.PRUNE_UPDATE)
                    .storageType(imageStorageService.getStorageType())
                    .storagePath(storagePath)
                    .mimeType(imageFile.getContentType())
                    .originalFilename(imageFile.getOriginalFilename())
                    .capturedAt(request.prunedAt())
                    .build();
            pruneImage = plantImageRepository.save(pruneImage);
            imageId = pruneImage.getId();
        }

        PlantPruneEvent event = PlantPruneEvent.builder()
                .plantId(plantId)
                .prunedAt(request.prunedAt())
                .notes(request.notes())
                .imageId(imageId)
                .build();
        pruneEventRepository.save(event);
        log.info("Prune event recorded for plant {}", plantId);

        jobPublisher.publish(PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_REMINDER_RECOMPUTE)
                .plantId(plantId)
                .build());
    }

    @Transactional
    public RequestPruningAnalysisResponse requestPruningAnalysis(Long plantId, List<MultipartFile> images) {
        findActivePlant(plantId);

        if (images == null || images.isEmpty() || images.size() > 3) {
            throw new IllegalArgumentException("Pruning analysis requires 1 to 3 images");
        }

        List<Long> imageIds = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            MultipartFile imageFile = images.get(i);
            validateImageFile(imageFile);
            String storagePath = imageStorageService.store(imageFile, plantId);
            PlantImage image = PlantImage.builder()
                    .plantId(plantId)
                    .imageType(PlantImage.ImageType.PRUNE_UPDATE)
                    .storageType(imageStorageService.getStorageType())
                    .storagePath(storagePath)
                    .mimeType(imageFile.getContentType())
                    .originalFilename(imageFile.getOriginalFilename())
                    .capturedAt(OffsetDateTime.now())
                    .sortOrder(i)
                    .build();
            image = plantImageRepository.save(image);
            imageIds.add(image.getId());
        }

        PlantAnalysis analysis = PlantAnalysis.builder()
                .plantId(plantId)
                .analysisType(PlantAnalysis.AnalysisType.PRUNING)
                .status(PlantAnalysis.AnalysisStatus.PENDING)
                .build();
        analysis = plantAnalysisRepository.save(analysis);

        jobPublisher.publish(PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PRUNING_ANALYSIS)
                .plantId(plantId)
                .analysisId(analysis.getId())
                .imageIds(imageIds)
                .build());

        log.info("Pruning analysis requested for plant {}, analysisId={}", plantId, analysis.getId());

        return new RequestPruningAnalysisResponse(
                analysis.getId(),
                plantId,
                analysis.getStatus().name(),
                imageIds.size(),
                analysis.getCreatedAt()
        );
    }

    @Transactional
    public void updatePlantName(Long plantId, String name) {
        Plant plant = findActivePlant(plantId);
        String trimmed = (name != null) ? name.trim() : null;
        plant.setName((trimmed == null || trimmed.isEmpty()) ? null : trimmed);
        plantRepository.save(plant);
        log.info("Plant name updated: id={}, name={}", plantId, plant.getName());
    }

    @Transactional
    public RequestReanalysisResponse requestReanalysis(Long plantId) {
        findActivePlant(plantId);

        PlantAnalysis analysis = PlantAnalysis.builder()
                .plantId(plantId)
                .analysisType(PlantAnalysis.AnalysisType.REANALYSIS)
                .status(PlantAnalysis.AnalysisStatus.PENDING)
                .build();
        analysis = plantAnalysisRepository.save(analysis);

        jobPublisher.publish(PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_REANALYSIS)
                .plantId(plantId)
                .analysisId(analysis.getId())
                .build());

        log.info("Reanalysis requested for plant {}, analysisId={}", plantId, analysis.getId());

        return new RequestReanalysisResponse(analysis.getId(), plantId, analysis.getStatus().name(), analysis.getCreatedAt());
    }

    @Transactional
    public void addHistoryNote(Long plantId, AddHistoryNoteRequest request) {
        findActivePlant(plantId);
        PlantHistoryEntry entry = PlantHistoryEntry.builder()
                .plantId(plantId)
                .noteText(request.noteText().trim())
                .build();
        historyEntryRepository.save(entry);
        log.info("History note added for plant {}", plantId);
    }

    @Transactional
    public void addHistoryImage(Long plantId, MultipartFile imageFile, String noteText) {
        findActivePlant(plantId);
        validateImageFile(imageFile);

        String storagePath = imageStorageService.store(imageFile, plantId);
        PlantImage image = PlantImage.builder()
                .plantId(plantId)
                .imageType(PlantImage.ImageType.HISTORY_NOTE)
                .storageType(imageStorageService.getStorageType())
                .storagePath(storagePath)
                .mimeType(imageFile.getContentType())
                .originalFilename(imageFile.getOriginalFilename())
                .capturedAt(OffsetDateTime.now())
                .sortOrder(0)
                .build();
        image = plantImageRepository.save(image);

        String trimmedNote = (noteText != null && !noteText.isBlank()) ? noteText.trim() : null;
        PlantHistoryEntry entry = PlantHistoryEntry.builder()
                .plantId(plantId)
                .imageId(image.getId())
                .noteText(trimmedNote)
                .build();
        historyEntryRepository.save(entry);
        log.info("History image added for plant {}", plantId);
    }

    @Transactional
    public void archivePlant(Long plantId) {
        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));
        plant.setStatus(PlantStatus.ARCHIVED);
        plant.setArchivedAt(OffsetDateTime.now());
        plantRepository.save(plant);
        log.info("Plant archived: {}", plantId);
    }

    @Transactional
    public void restorePlant(Long plantId) {
        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));
        plant.setStatus(PlantStatus.ACTIVE);
        plant.setArchivedAt(null);
        plantRepository.save(plant);
        log.info("Plant restored: {}", plantId);
    }

    private Plant findActivePlant(Long plantId) {
        return plantRepository.findByIdAndStatus(plantId, PlantStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active plant not found: " + plantId));
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid image type: " + contentType + ". Allowed: " + ALLOWED_MIME_TYPES);
        }
    }
}
