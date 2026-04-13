package com.planted.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planted.queue.PlantJobEvent;
import com.planted.queue.PlantJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Routes background jobs to the correct processor.
 *
 * In local profile: listens to Spring ApplicationEvents published by LocalPlantJobPublisher.
 * In prod profile: an SQS polling listener (or Lambda integration) would call process() directly.
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
    private final ObjectMapper objectMapper;

    /**
     * Local dev: listens for Spring application events from LocalPlantJobPublisher.
     * Runs async so the HTTP request thread is not blocked.
     */
    @Async("plantJobExecutor")
    @EventListener
    public void onLocalJobEvent(PlantJobEvent event) {
        process(event.getMessage());
    }

    /**
     * Core dispatch method — routes to the correct processor.
     * Called by onLocalJobEvent (local) or an SQS polling bean (prod).
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
                default ->
                        log.warn("Unknown job type: {}", message.getJobType());
            }
        } catch (Exception e) {
            log.error("Job processing failed for type={} plantId={}", message.getJobType(), message.getPlantId(), e);
        }
    }
}
