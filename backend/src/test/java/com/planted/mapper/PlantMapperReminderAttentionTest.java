package com.planted.mapper;

import com.planted.dto.ReminderStateDto;
import com.planted.entity.PlantReminderState;
import com.planted.repository.PlantImageRepository;
import com.planted.storage.ImageStorageService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the mapping from {@link PlantReminderState} to {@link ReminderStateDto}
 * for the health / light / placement attention flags + reasons. These fields
 * drive the six-icon plant-list / screensaver indicator row and must round-trip
 * unchanged through the DTO boundary.
 */
class PlantMapperReminderAttentionTest {

    private final PlantMapper mapper = new PlantMapper(
            (ImageStorageService) null, (PlantImageRepository) null);

    @Test
    void reminderState_allAttentionFlagsAndReasons_mapToDto() {
        PlantReminderState state = PlantReminderState.builder()
                .plantId(1L)
                .wateringDue(true)
                .wateringOverdue(false)
                .fertilizerDue(true)
                .pruningDue(false)
                .healthAttentionNeeded(true)
                .lightAttentionNeeded(true)
                .placementAttentionNeeded(true)
                .healthAttentionReason("Yellowing lower leaves")
                .lightAttentionReason("Likely too dark")
                .placementAttentionReason("Outgrowing its pot")
                .lastComputedAt(OffsetDateTime.now())
                .build();

        ReminderStateDto dto = mapper.toReminderStateDto(state);

        assertThat(dto).isNotNull();
        assertThat(dto.healthAttentionNeeded()).isTrue();
        assertThat(dto.lightAttentionNeeded()).isTrue();
        assertThat(dto.placementAttentionNeeded()).isTrue();
        assertThat(dto.healthAttentionReason()).isEqualTo("Yellowing lower leaves");
        assertThat(dto.lightAttentionReason()).isEqualTo("Likely too dark");
        assertThat(dto.placementAttentionReason()).isEqualTo("Outgrowing its pot");
        assertThat(dto.wateringDue()).isTrue();
        assertThat(dto.fertilizerDue()).isTrue();
    }

    @Test
    void reminderState_noAttentionFlags_mapsToFalseAndNullReasons() {
        PlantReminderState state = PlantReminderState.builder()
                .plantId(2L)
                .lastComputedAt(OffsetDateTime.now())
                .build();

        ReminderStateDto dto = mapper.toReminderStateDto(state);

        assertThat(dto.healthAttentionNeeded()).isFalse();
        assertThat(dto.lightAttentionNeeded()).isFalse();
        assertThat(dto.placementAttentionNeeded()).isFalse();
        assertThat(dto.healthAttentionReason()).isNull();
        assertThat(dto.lightAttentionReason()).isNull();
        assertThat(dto.placementAttentionReason()).isNull();
    }

    @Test
    void reminderState_nullInput_returnsNull() {
        assertThat(mapper.toReminderStateDto(null)).isNull();
    }
}
