package com.planted.service;

import com.planted.entity.PlantWateringEvent;
import com.planted.repository.PlantFertilizerEventRepository;
import com.planted.repository.PlantPruneEventRepository;
import com.planted.repository.PlantWateringEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareHistoryFormatterTest {

    @Mock
    private PlantWateringEventRepository wateringEventRepository;
    @Mock
    private PlantFertilizerEventRepository fertilizerEventRepository;
    @Mock
    private PlantPruneEventRepository pruneEventRepository;

    private CareHistoryFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new CareHistoryFormatter(wateringEventRepository, fertilizerEventRepository, pruneEventRepository);
    }

    @Test
    void formatForLlm_noWaterings_showsNeverRecorded() {
        when(wateringEventRepository.findByPlantIdOrderByWateredAtDesc(1L)).thenReturn(List.of());
        when(fertilizerEventRepository.findFirstByPlantIdOrderByFertilizedAtDesc(1L)).thenReturn(Optional.empty());
        when(pruneEventRepository.findFirstByPlantIdOrderByPrunedAtDesc(1L)).thenReturn(Optional.empty());

        String s = formatter.formatForLlm(1L);

        assertThat(s).contains("Recent watering dates (newest first): Never recorded");
        assertThat(s).contains("Last fertilized: Never recorded");
        assertThat(s).contains("Last pruned: Never recorded");
    }

    @Test
    void formatForLlm_multipleWaterings_joinsWithSemicolons() {
        OffsetDateTime t1 = OffsetDateTime.parse("2026-04-10T12:00:00Z");
        OffsetDateTime t2 = OffsetDateTime.parse("2026-04-03T08:00:00Z");
        when(wateringEventRepository.findByPlantIdOrderByWateredAtDesc(2L))
                .thenReturn(List.of(
                        PlantWateringEvent.builder().plantId(2L).wateredAt(t1).build(),
                        PlantWateringEvent.builder().plantId(2L).wateredAt(t2).build()));
        when(fertilizerEventRepository.findFirstByPlantIdOrderByFertilizedAtDesc(2L)).thenReturn(Optional.empty());
        when(pruneEventRepository.findFirstByPlantIdOrderByPrunedAtDesc(2L)).thenReturn(Optional.empty());

        String s = formatter.formatForLlm(2L);

        assertThat(s).contains("April 10, 2026");
        assertThat(s).contains("April 3, 2026");
        assertThat(s).contains(";");
    }
}
