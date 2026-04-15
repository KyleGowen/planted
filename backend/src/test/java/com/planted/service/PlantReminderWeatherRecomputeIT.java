package com.planted.service;

import com.planted.entity.*;
import com.planted.repository.*;
import com.planted.weather.WeatherService;
import com.planted.weather.WeatherSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles({"local", "test"})
@Testcontainers(disabledWithoutDocker = true)
class PlantReminderWeatherRecomputeIT {

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
    private PlantWateringEventRepository wateringEventRepository;

    @Autowired
    private PlantReminderStateRepository reminderStateRepository;

    @MockBean
    private WeatherService weatherService;

    @Test
    void outdoorHeavyRain_softensWateringDueAndSetsWeatherNote() {
        when(weatherService.fetchSnapshot(anyDouble(), anyDouble()))
                .thenReturn(Optional.of(new WeatherSnapshot(20, 20, 0, false)));

        Plant plant = plantRepository.save(Plant.builder()
                .userId("default")
                .name("Outdoor fern")
                .status(PlantStatus.ACTIVE)
                .growingContext(PlantGrowingContext.OUTDOOR)
                .latitude(40.7)
                .longitude(-74.0)
                .build());

        reminderStateRepository.save(PlantReminderState.builder().plantId(plant.getId()).build());

        plantAnalysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .wateringFrequency("every 7 days")
                .wateringAmount("until moist")
                .completedAt(OffsetDateTime.now())
                .build());

        wateringEventRepository.save(PlantWateringEvent.builder()
                .plantId(plant.getId())
                .wateredAt(OffsetDateTime.now().minusDays(7))
                .notes("test")
                .build());

        plantReminderService.recomputeReminderState(plant.getId());

        PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElseThrow();
        assertThat(state.isWateringDue()).isFalse();
        assertThat(state.getNextWateringInstruction()).contains("recent rain");
        assertThat(state.getWeatherCareNote()).isNotBlank();
    }

    @Test
    void indoor_skipsWeatherClient() {
        Plant plant = plantRepository.save(Plant.builder()
                .userId("default")
                .name("Indoor palm")
                .status(PlantStatus.ACTIVE)
                .growingContext(PlantGrowingContext.INDOOR)
                .build());

        reminderStateRepository.save(PlantReminderState.builder().plantId(plant.getId()).build());

        plantAnalysisRepository.save(PlantAnalysis.builder()
                .plantId(plant.getId())
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .wateringFrequency("every 7 days")
                .wateringAmount("1 cup")
                .completedAt(OffsetDateTime.now())
                .build());

        wateringEventRepository.save(PlantWateringEvent.builder()
                .plantId(plant.getId())
                .wateredAt(OffsetDateTime.now().minusDays(7))
                .notes("test")
                .build());

        plantReminderService.recomputeReminderState(plant.getId());

        verify(weatherService, never()).fetchSnapshot(anyDouble(), anyDouble());
        PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElseThrow();
        assertThat(state.getWeatherCareNote()).isNull();
    }
}
