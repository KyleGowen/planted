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

    /**
     * Core dispatch method — routes to the correct processor.
     */
    public void process(PlantJobMessage message) {
        log.info("Processing job: {} for plantId={}", message.getJobType(), message.getPlantId());
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
                default ->
                        log.warn("Unknown job type: {}", message.getJobType());
            }
        } catch (Exception e) {
            log.error("Job processing failed for type={} plantId={}", message.getJobType(), message.getPlantId(), e);
        }
    }
}
