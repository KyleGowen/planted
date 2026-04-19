package com.planted.worker;

import com.planted.entity.PlantBioSectionKey;
import com.planted.queue.PlantJobMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins the job-type -> processor routing in {@link PlantJobWorker}. When we add new
 * job types or change how bio-section refreshes flow through the worker, this test
 * catches accidental rewires.
 */
@ExtendWith(MockitoExtension.class)
class PlantJobWorkerBioSectionDispatchTest {

    @Mock private PlantAnalysisProcessor analysisProcessor;
    @Mock private PlantIllustrationProcessor illustrationProcessor;
    @Mock private PlantPruningProcessor pruningProcessor;
    @Mock private PlantReminderRecomputeProcessor reminderRecomputeProcessor;
    @Mock private HealthyReferenceImageProcessor healthyReferenceImageProcessor;
    @Mock private PlantHistorySummaryProcessor historySummaryProcessor;
    @Mock @SuppressWarnings("deprecation") private PlacementNotesSummaryProcessor placementNotesSummaryProcessor;
    @Mock private PlantBioSectionProcessor bioSectionProcessor;

    @InjectMocks
    private PlantJobWorker worker;

    @Test
    void bioSectionRefreshJob_dispatchesToBioSectionProcessor() {
        PlantJobMessage message = PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_BIO_SECTION_REFRESH)
                .plantId(42L)
                .sectionKey(PlantBioSectionKey.WATER_CARE)
                .build();

        worker.process(message);

        ArgumentCaptor<PlantJobMessage> captor = ArgumentCaptor.forClass(PlantJobMessage.class);
        verify(bioSectionProcessor).process(captor.capture());
        assertThat(captor.getValue().getSectionKey()).isEqualTo(PlantBioSectionKey.WATER_CARE);

        verify(analysisProcessor, never()).process(message);
        verify(historySummaryProcessor, never()).process(message);
        verify(placementNotesSummaryProcessor, never()).process(message);
    }

    @Test
    void registrationAnalysisJob_stillDispatchesToAnalysisProcessor() {
        PlantJobMessage message = PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_REGISTRATION_ANALYSIS)
                .plantId(1L)
                .build();

        worker.process(message);

        verify(analysisProcessor).process(message);
        verify(bioSectionProcessor, never()).process(message);
    }

    @Test
    @SuppressWarnings("deprecation")
    void placementNotesSummaryJob_stillRuntimeRoutable() {
        PlantJobMessage message = PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLACEMENT_NOTES_SUMMARY)
                .plantId(3L)
                .build();

        worker.process(message);

        verify(placementNotesSummaryProcessor).process(message);
        verify(bioSectionProcessor, never()).process(message);
    }

    @Test
    void exceptionInProcessor_doesNotPropagate() {
        PlantJobMessage message = PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_BIO_SECTION_REFRESH)
                .plantId(99L)
                .sectionKey(PlantBioSectionKey.SPECIES_ID)
                .build();
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(bioSectionProcessor).process(message);

        worker.process(message);

        verify(bioSectionProcessor).process(message);
    }
}
