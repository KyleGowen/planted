package com.planted.service;

import com.planted.dto.RecordFertilizerRequest;
import com.planted.dto.RecordPruneRequest;
import com.planted.dto.RecordWateringRequest;
import com.planted.entity.Plant;
import com.planted.entity.PlantStatus;
import com.planted.queue.PlantJobPublisher;
import com.planted.repository.*;
import com.planted.storage.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlantCommandServiceCareEventTest {

    @Mock
    private PlantRepository plantRepository;
    @Mock
    private PlantImageRepository plantImageRepository;
    @Mock
    private PlantAnalysisRepository plantAnalysisRepository;
    @Mock
    private PlantWateringEventRepository wateringEventRepository;
    @Mock
    private PlantFertilizerEventRepository fertilizerEventRepository;
    @Mock
    private PlantPruneEventRepository pruneEventRepository;
    @Mock
    private PlantReminderStateRepository reminderStateRepository;
    @Mock
    private PlantHistoryEntryRepository historyEntryRepository;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private PlantJobPublisher jobPublisher;
    @Mock
    private PlantReminderService plantReminderService;

    @InjectMocks
    private PlantCommandService plantCommandService;

    @Test
    void recordWatering_persistsAndRecomputesRemindersSynchronously() {
        long plantId = 42L;
        when(plantRepository.findByIdAndStatus(plantId, PlantStatus.ACTIVE))
                .thenReturn(Optional.of(Plant.builder().id(plantId).status(PlantStatus.ACTIVE).build()));
        when(wateringEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        plantCommandService.recordWatering(
                plantId, new RecordWateringRequest(OffsetDateTime.parse("2026-04-10T15:00:00Z"), null));

        verify(wateringEventRepository).save(any());
        verify(plantReminderService).recomputeReminderState(plantId);
        verify(jobPublisher, never()).publish(any());
    }

    @Test
    void recordFertilizer_recomputesRemindersWithoutPublishingJob() {
        long plantId = 7L;
        when(plantRepository.findByIdAndStatus(plantId, PlantStatus.ACTIVE))
                .thenReturn(Optional.of(Plant.builder().id(plantId).status(PlantStatus.ACTIVE).build()));
        when(fertilizerEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        plantCommandService.recordFertilizer(
                plantId,
                new RecordFertilizerRequest(OffsetDateTime.parse("2026-04-01T12:00:00Z"), null, null));

        verify(plantReminderService).recomputeReminderState(plantId);
        verify(jobPublisher, never()).publish(any());
    }

    @Test
    void recordPrune_recomputesRemindersWithoutPublishingJob() {
        long plantId = 9L;
        when(plantRepository.findByIdAndStatus(plantId, PlantStatus.ACTIVE))
                .thenReturn(Optional.of(Plant.builder().id(plantId).status(PlantStatus.ACTIVE).build()));
        when(pruneEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        plantCommandService.recordPrune(
                plantId, new RecordPruneRequest(OffsetDateTime.parse("2026-03-20T10:00:00Z"), null), null);

        verify(plantReminderService).recomputeReminderState(plantId);
        verify(jobPublisher, never()).publish(any());
    }
}
