package com.planted.service;

import com.planted.entity.PlantBioSection;
import com.planted.entity.PlantBioSectionKey;
import com.planted.queue.PlantJobMessage;
import com.planted.queue.PlantJobPublisher;
import com.planted.repository.PlantBioSectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BioSectionInvalidator} is the single chokepoint for marking cached bio
 * sections stale when a plant mutation changes the inputs those sections depend
 * on. These tests pin the exact set of sections each mutation invalidates so
 * future changes to the dependency graph are deliberate.
 */
@ExtendWith(MockitoExtension.class)
class BioSectionInvalidatorTest {

    @Mock
    private PlantBioSectionRepository sectionRepository;

    @Mock
    private PlantJobPublisher jobPublisher;

    @InjectMocks
    private BioSectionInvalidator invalidator;

    @Test
    void onGrowingChanged_invalidatesEveryCareSection() {
        long plantId = 1L;
        Set<PlantBioSectionKey> expected = EnumSet.of(
                PlantBioSectionKey.WATER_CARE,
                PlantBioSectionKey.FERTILIZER_CARE,
                PlantBioSectionKey.PRUNING_CARE,
                PlantBioSectionKey.LIGHT_CARE,
                PlantBioSectionKey.PLACEMENT_CARE);
        for (PlantBioSectionKey key : expected) {
            stubExistingRow(plantId, key);
        }

        invalidator.onGrowingChanged(plantId);

        Set<PlantBioSectionKey> saved = capturedSavedKeys(expected.size());
        assertThat(saved).containsExactlyInAnyOrderElementsOf(expected);
        verify(jobPublisher, never()).publish(any());
    }

    @Test
    void onPlacementChanged_invalidatesPlacementLightAndWater() {
        long plantId = 2L;
        Set<PlantBioSectionKey> expected = EnumSet.of(
                PlantBioSectionKey.PLACEMENT_CARE,
                PlantBioSectionKey.LIGHT_CARE,
                PlantBioSectionKey.WATER_CARE);
        for (PlantBioSectionKey key : expected) {
            stubExistingRow(plantId, key);
        }

        invalidator.onPlacementChanged(plantId);

        Set<PlantBioSectionKey> saved = capturedSavedKeys(expected.size());
        assertThat(saved).containsExactlyInAnyOrderElementsOf(expected);
        verify(jobPublisher, never()).publish(any());
    }

    @Test
    void onJournalChanged_invalidatesHistorySummaryAndSpeciesId() {
        // A journal note can carry an owner-asserted species correction
        // (OWNER CERTAINTY — e.g. "actually a Sansevieria cylindrica"),
        // so SPECIES_ID must go stale too; the species cascade from the
        // processor handles dependent sections when the species actually
        // changes. Nothing is enqueued here — the lazy-refresh on next read
        // picks it up, which keeps tokens unspent on plants nobody views.
        long plantId = 3L;
        Set<PlantBioSectionKey> expected = EnumSet.of(
                PlantBioSectionKey.HISTORY_SUMMARY,
                PlantBioSectionKey.SPECIES_ID);
        for (PlantBioSectionKey key : expected) {
            stubExistingRow(plantId, key);
        }

        invalidator.onJournalChanged(plantId);

        Set<PlantBioSectionKey> saved = capturedSavedKeys(expected.size());
        assertThat(saved).containsExactlyInAnyOrderElementsOf(expected);
        verify(jobPublisher, never()).publish(any());
    }

    @Test
    void onReanalysisRequested_enqueuesEveryVisionSection() {
        long plantId = 4L;

        invalidator.onReanalysisRequested(plantId);

        ArgumentCaptor<PlantJobMessage> captor = ArgumentCaptor.forClass(PlantJobMessage.class);
        verify(jobPublisher, org.mockito.Mockito.times(4)).publish(captor.capture());
        List<PlantJobMessage> msgs = captor.getAllValues();
        assertThat(msgs).allSatisfy(m -> {
            assertThat(m.getJobType()).isEqualTo(PlantJobMessage.JobType.PLANT_BIO_SECTION_REFRESH);
            assertThat(m.getPlantId()).isEqualTo(plantId);
        });
        assertThat(msgs).extracting(PlantJobMessage::getSectionKey)
                .containsExactlyInAnyOrder(
                        PlantBioSectionKey.SPECIES_ID,
                        PlantBioSectionKey.HEALTH_ASSESSMENT,
                        PlantBioSectionKey.LIGHT_CARE,
                        PlantBioSectionKey.PLACEMENT_CARE);
    }

    @Test
    void invalidate_noopWhenRowMissing() {
        long plantId = 5L;
        when(sectionRepository.findByPlantIdAndSectionKey(
                eq(plantId), eq(PlantBioSectionKey.WATER_CARE))).thenReturn(Optional.empty());

        invalidator.invalidate(plantId, PlantBioSectionKey.WATER_CARE);

        verify(sectionRepository, never()).save(any());
    }

    @Test
    void invalidate_zeroesOutFingerprintAndGeneratedAt() {
        long plantId = 6L;
        PlantBioSection row = PlantBioSection.builder()
                .plantId(plantId)
                .sectionKey(PlantBioSectionKey.WATER_CARE)
                .status(PlantBioSection.Status.COMPLETED)
                .inputsFingerprint("abc123")
                .generatedAt(OffsetDateTime.now())
                .build();
        when(sectionRepository.findByPlantIdAndSectionKey(
                eq(plantId), eq(PlantBioSectionKey.WATER_CARE))).thenReturn(Optional.of(row));

        invalidator.invalidate(plantId, PlantBioSectionKey.WATER_CARE);

        ArgumentCaptor<PlantBioSection> captor = ArgumentCaptor.forClass(PlantBioSection.class);
        verify(sectionRepository).save(captor.capture());
        assertThat(captor.getValue().getGeneratedAt()).isNull();
        assertThat(captor.getValue().getInputsFingerprint()).isNull();
    }

    private void stubExistingRow(Long plantId, PlantBioSectionKey key) {
        PlantBioSection row = PlantBioSection.builder()
                .plantId(plantId)
                .sectionKey(key)
                .status(PlantBioSection.Status.COMPLETED)
                .inputsFingerprint("seed-" + key.name())
                .generatedAt(OffsetDateTime.now())
                .build();
        when(sectionRepository.findByPlantIdAndSectionKey(eq(plantId), eq(key)))
                .thenReturn(Optional.of(row));
    }

    private Set<PlantBioSectionKey> capturedSavedKeys(int expectedCount) {
        ArgumentCaptor<PlantBioSection> captor = ArgumentCaptor.forClass(PlantBioSection.class);
        verify(sectionRepository, org.mockito.Mockito.times(expectedCount)).save(captor.capture());
        Set<PlantBioSectionKey> keys = EnumSet.noneOf(PlantBioSectionKey.class);
        for (PlantBioSection row : captor.getAllValues()) {
            keys.add(row.getSectionKey());
            assertThat(row.getGeneratedAt()).isNull();
            assertThat(row.getInputsFingerprint()).isNull();
        }
        return keys;
    }
}
