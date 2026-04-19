package com.planted.worker;

import com.planted.queue.PlantJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Routes background jobs to the correct processor.
 *
 * <p>Local profile: {@link com.planted.queue.LocalPlantJobPublisher} schedules {@link #process}
 * on {@code plantJobExecutor} after transaction commit.
 * Prod profile: an SQS polling listener (or Lambda) should call {@link #process} directly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlantJobWorker {

    private final PlantAnalysisProcessor analysisProcessor;
    private final PlantIllustrationProcessor illustrationProcessor;
    private final PlantPruningProcessor pruningProcessor;
    private final PlantReminderRecomputeProcessor reminderRecomputeProcessor;
    private final HealthyReferenceImageProcessor healthyReferenceImageProcessor;
    private final PlantHistorySummaryProcessor historySummaryProcessor;
    /** @deprecated kept for one release; see {@link PlacementNotesSummaryProcessor}. */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("deprecation")
    private final PlacementNotesSummaryProcessor placementNotesSummaryProcessor;
    private final PlantBioSectionProcessor bioSectionProcessor;

    /**
     * Core dispatch method — routes to the correct processor.
     */
    @SuppressWarnings("deprecation")
    public void process(PlantJobMessage message) {
        log.info("Processing job: {} for plantId={} sectionKey={}",
                message.getJobType(), message.getPlantId(), message.getSectionKey());
        try {
            switch (message.getJobType()) {
                case PLANT_REGISTRATION_ANALYSIS, PLANT_REANALYSIS ->
                        analysisProcessor.process(message);
                case PLANT_ILLUSTRATION_GENERATION ->
                        illustrationProcessor.process(message);
                case PRUNING_ANALYSIS ->
                        pruningProcessor.process(message);
                case PLANT_REMINDER_RECOMPUTE ->
                        reminderRecomputeProcessor.process(message);
                case HEALTHY_REFERENCE_IMAGE_FETCH ->
                        healthyReferenceImageProcessor.process(message);
                case PLANT_HISTORY_SUMMARY ->
                        historySummaryProcessor.process(message);
                case PLACEMENT_NOTES_SUMMARY ->
                        placementNotesSummaryProcessor.process(message);
                case PLANT_BIO_SECTION_REFRESH ->
                        bioSectionProcessor.process(message);
                default ->
                        log.warn("Unknown job type: {}", message.getJobType());
            }
        } catch (Exception e) {
            log.error("Job processing failed for type={} plantId={}", message.getJobType(), message.getPlantId(), e);
        }
    }
}
