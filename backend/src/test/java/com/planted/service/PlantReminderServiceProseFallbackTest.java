package com.planted.service;

import com.planted.entity.*;
import com.planted.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the ginseng-bonsai case where the LLM omits watering/fertilizer
 * frequencies but still returns prose guidance. The reminder recompute should populate
 * next*Instruction from that prose so the care rows still render on the plant detail page.
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Testcontainers(disabledWithoutDocker = true)
class PlantReminderServiceProseFallbackTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private PlantReminderService plantReminderService;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private PlantAnalysisRepository plantAnalysisRepository;

    @Autowired
    private PlantReminderStateRepository reminderStateRepository;

    @Test
    void wateringProse_withoutFrequency_populatesInstruction() {
        Plant plant = newIndoorPlant("Ginseng bonsai");
        reminderStateRepository.save(PlantReminderState.builder().plantId(plant.getId()).build());

        plantAnalysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .wateringGuidance("Water thoroughly when the top 2 cm of soil feels dry.")
                .completedAt(OffsetDateTime.now())
                .build());

        plantReminderService.recomputeReminderState(plant.getId());

        PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElseThrow();
        assertThat(state.getNextWateringInstruction())
                .isEqualTo("Water thoroughly when the top 2 cm of soil feels dry.");
        assertThat(state.isWateringDue()).isFalse();
        assertThat(state.isWateringOverdue()).isFalse();
    }

    @Test
    void fertilizerProse_withoutFrequency_populatesInstruction() {
        Plant plant = newIndoorPlant("Ginseng bonsai");
        reminderStateRepository.save(PlantReminderState.builder().plantId(plant.getId()).build());

        plantAnalysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .fertilizerGuidance("Feed with a balanced liquid fertilizer during active growth.")
                .completedAt(OffsetDateTime.now())
                .build());

        plantReminderService.recomputeReminderState(plant.getId());

        PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElseThrow();
        assertThat(state.getNextFertilizerInstruction())
                .isEqualTo("Feed with a balanced liquid fertilizer during active growth.");
        assertThat(state.isFertilizerDue()).isFalse();
    }

    @Test
    void emptyProse_leavesInstructionsNull() {
        Plant plant = newIndoorPlant("Plant without guidance");
        reminderStateRepository.save(PlantReminderState.builder().plantId(plant.getId()).build());

        plantAnalysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .completedAt(OffsetDateTime.now())
                .build());

        plantReminderService.recomputeReminderState(plant.getId());

        PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElseThrow();
        assertThat(state.getNextWateringInstruction()).isNull();
        assertThat(state.getNextFertilizerInstruction()).isNull();
        assertThat(state.getNextPruningInstruction()).isNull();
    }

    private Plant newIndoorPlant(String name) {
        return plantRepository.save(Plant.builder()
                .userId("default")
                .name(name)
                .status(PlantStatus.ACTIVE)
                .growingContext(PlantGrowingContext.INDOOR)
                .build());
    }
}
