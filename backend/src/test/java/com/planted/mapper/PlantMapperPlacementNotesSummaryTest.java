package com.planted.mapper;

import com.planted.dto.PlantDetailResponse;
import com.planted.entity.Plant;
import com.planted.entity.PlantStatus;
import com.planted.repository.PlantImageRepository;
import com.planted.storage.ImageStorageService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the dedicated placement notes summary on {@link Plant} survives the
 * entity -> DTO hop that feeds the third line under the Indoor/Outdoor header.
 */
class PlantMapperPlacementNotesSummaryTest {

    private final PlantMapper mapper = new PlantMapper(
            (ImageStorageService) null, (PlantImageRepository) null);

    @Test
    void detailResponseCarriesPlacementNotesSummary() {
        Plant plant = buildPlant("Living room east window with morning sun");

        PlantDetailResponse response = mapper.toDetailResponse(
                plant,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                false,
                List.of(),
                null,
                List.of(),
                null,
                null,
                false);

        assertEquals("Living room east window with morning sun", response.placementNotesSummary());
    }

    @Test
    void detailResponsePropagatesNullWhenNoSummary() {
        Plant plant = buildPlant(null);

        PlantDetailResponse response = mapper.toDetailResponse(
                plant,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                false,
                List.of(),
                null,
                List.of(),
                null,
                null,
                false);

        assertNull(response.placementNotesSummary());
    }

    private Plant buildPlant(String placementNotesSummary) {
        Plant plant = Plant.builder()
                .id(7L)
                .userId("default")
                .location("Outside Kyle's office in between the two houses on the west side of the main house")
                .placementNotesSummary(placementNotesSummary)
                .status(PlantStatus.ACTIVE)
                .build();
        plant.setCreatedAt(OffsetDateTime.now());
        plant.setUpdatedAt(OffsetDateTime.now());
        return plant;
    }
}
