package com.planted.controller;

import com.planted.entity.Plant;
import com.planted.entity.PlantAnalysis;
import com.planted.entity.PlantStatus;
import com.planted.repository.PlantAnalysisRepository;
import com.planted.repository.PlantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression: single-result JPA expectations must not throw when many analyses exist for one plant
 * (several INFO_PANEL summaries, or many registration/reanalysis rows).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers(disabledWithoutDocker = true)
class PlantDetailGetDuplicateInfoPanelIT {

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
    private MockMvc mockMvc;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private PlantAnalysisRepository analysisRepository;

    @Test
    void getPlant_returns200WhenMultipleCompletedInfoPanelSummariesExist() throws Exception {
        Plant plant = new Plant();
        plant.setName("Dup info panel");
        plant.setStatus(PlantStatus.ACTIVE);
        plant = plantRepository.save(plant);

        analysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.INFO_PANEL)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .infoPanelSummary("Older summary body")
                .completedAt(OffsetDateTime.parse("2024-01-01T12:00:00Z"))
                .build());

        analysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.INFO_PANEL)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .infoPanelSummary("Newer summary body")
                .completedAt(OffsetDateTime.parse("2025-06-01T12:00:00Z"))
                .build());

        mockMvc.perform(get("/api/plants/" + plant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plant.getId()))
                .andExpect(jsonPath("$.historySummaryText").value("Newer summary body"));
    }

    @Test
    void getPlant_returns200WhenManyRegistrationAndReanalysisRowsExist() throws Exception {
        Plant plant = new Plant();
        plant.setName("Many care analyses");
        plant.setStatus(PlantStatus.ACTIVE);
        plant = plantRepository.save(plant);

        for (int i = 0; i < 5; i++) {
            PlantAnalysis.AnalysisType type = i % 2 == 0
                    ? PlantAnalysis.AnalysisType.REGISTRATION
                    : PlantAnalysis.AnalysisType.REANALYSIS;
            analysisRepository.save(PlantAnalysis.builder()
                    .plantId(plant.getId())
                    .analysisType(type)
                    .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                    .completedAt(OffsetDateTime.parse("2024-0" + (i + 1) + "-01T12:00:00Z"))
                    .lightNeeds("snap-" + i)
                    .build());
        }

        mockMvc.perform(get("/api/plants/" + plant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plant.getId()))
                .andExpect(jsonPath("$.latestAnalysis.lightNeeds").value("snap-4"));
    }
}
