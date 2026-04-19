package com.planted.service;

import com.planted.entity.PlantBioSection;
import com.planted.entity.PlantBioSectionKey;
import com.planted.queue.PlantJobMessage;
import com.planted.queue.PlantJobPublisher;
import com.planted.repository.PlantBioSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Single place that knows which bio sections become stale for a given plant
 * mutation. "Invalidate" here means: zero out {@code generated_at} so the lazy
 * refresh on the next read picks the section up. Nothing is enqueued
 * immediately — that keeps tokens unspent on plants nobody is viewing. The one
 * exception is {@link #enqueueRefresh(Long, PlantBioSectionKey)} which is used
 * by the registration flow and reanalysis when we explicitly want to race the
 * refresh in the background.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BioSectionInvalidator {

    private final PlantBioSectionRepository sectionRepository;
    private final PlantJobPublisher jobPublisher;

    @Transactional
    public void invalidate(Long plantId, PlantBioSectionKey key) {
        sectionRepository.findByPlantIdAndSectionKey(plantId, key).ifPresent(row -> {
            row.setGeneratedAt(null);
            row.setInputsFingerprint(null);
            sectionRepository.save(row);
        });
    }

    @Transactional
    public void invalidate(Long plantId, List<PlantBioSectionKey> keys) {
        for (PlantBioSectionKey k : keys) invalidate(plantId, k);
    }

    /** Proactively enqueue a refresh job (used for registration, reanalysis, and species cascade). */
    public void enqueueRefresh(Long plantId, PlantBioSectionKey key) {
        jobPublisher.publish(PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_BIO_SECTION_REFRESH)
                .plantId(plantId)
                .sectionKey(key)
                .build());
    }

    /** Growing context changed (indoor/outdoor, lighting, soil): refresh every care section. */
    public void onGrowingChanged(Long plantId) {
        invalidate(plantId, List.of(
                PlantBioSectionKey.WATER_CARE,
                PlantBioSectionKey.FERTILIZER_CARE,
                PlantBioSectionKey.PRUNING_CARE,
                PlantBioSectionKey.LIGHT_CARE,
                PlantBioSectionKey.PLACEMENT_CARE));
    }

    /** Placement notes changed: refresh placement-dependent sections. */
    public void onPlacementChanged(Long plantId) {
        invalidate(plantId, List.of(
                PlantBioSectionKey.PLACEMENT_CARE,
                PlantBioSectionKey.LIGHT_CARE,
                PlantBioSectionKey.WATER_CARE));
    }

    /** Journal entry added: history summary is stale. */
    public void onJournalChanged(Long plantId) {
        invalidate(plantId, PlantBioSectionKey.HISTORY_SUMMARY);
    }

    /**
     * Reanalysis requested: refresh the two vision sections immediately. The
     * SPECIES_ID processor will cascade-invalidate the remaining text sections
     * when (and only when) the resolved species actually changes.
     */
    public void onReanalysisRequested(Long plantId) {
        enqueueRefresh(plantId, PlantBioSectionKey.SPECIES_ID);
        enqueueRefresh(plantId, PlantBioSectionKey.HEALTH_ASSESSMENT);
    }
}
