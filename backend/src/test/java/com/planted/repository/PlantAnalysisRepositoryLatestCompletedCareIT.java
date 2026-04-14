package com.planted.repository;

import com.planted.entity.Plant;
import com.planted.entity.PlantAnalysis;
import com.planted.entity.PlantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures history summary picks the latest <em>completed</em> registration/reanalysis by completedAt.
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Testcontainers(disabledWithoutDocker = true)
class PlantAnalysisRepositoryLatestCompletedCareIT {

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
    private PlantRepository plantRepository;

    @Autowired
    private PlantAnalysisRepository analysisRepository;

    @Test
    void findLatestCompletedRegistrationOrReanalysis_prefersNewerCompletedAt() {
        Plant plant = new Plant();
        plant.setName("P");
        plant.setStatus(PlantStatus.ACTIVE);
        plant = plantRepository.save(plant);

        PlantAnalysis older = analysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .completedAt(OffsetDateTime.parse("2024-01-01T12:00:00Z"))
                .lightNeeds("older")
                .build());

        PlantAnalysis newer = analysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.REANALYSIS)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .completedAt(OffsetDateTime.parse("2025-06-01T12:00:00Z"))
                .lightNeeds("newer")
                .build());

        analysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.PENDING)
                .lightNeeds("pending")
                .build());

        var found = analysisRepository.findFirstByPlantIdAndAnalysisTypeInAndStatusOrderByCompletedAtDescIdDesc(
                plant.getId(),
                List.of(PlantAnalysis.AnalysisType.REGISTRATION, PlantAnalysis.AnalysisType.REANALYSIS),
                PlantAnalysis.AnalysisStatus.COMPLETED);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(newer.getId());
        assertThat(found.get().getLightNeeds()).isEqualTo("newer");
        assertThat(found.get().getId()).isNotEqualTo(older.getId());
    }

    @Test
    void findLatestCompletedHistorySummaryWithBody_limitsToOneWhenMultipleSummariesExist() {
        Plant plant = new Plant();
        plant.setName("Multi-summary");
        plant.setStatus(PlantStatus.ACTIVE);
        plant = plantRepository.save(plant);

        analysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.INFO_PANEL)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .infoPanelSummary("First narrative")
                .completedAt(OffsetDateTime.parse("2024-01-01T12:00:00Z"))
                .build());

        PlantAnalysis expectedNewer = analysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.INFO_PANEL)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .infoPanelSummary("Second narrative")
                .completedAt(OffsetDateTime.parse("2025-06-01T12:00:00Z"))
                .build());

        var rows = analysisRepository.findLatestCompletedHistorySummaryWithBody(
                plant.getId(),
                PlantAnalysis.AnalysisType.INFO_PANEL,
                PlantAnalysis.AnalysisStatus.COMPLETED,
                PageRequest.of(0, 1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(expectedNewer.getId());
        assertThat(rows.get(0).getInfoPanelSummary()).isEqualTo("Second narrative");
    }
}
