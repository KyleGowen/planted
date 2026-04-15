package com.planted.controller;

import com.planted.entity.Plant;
import com.planted.entity.PlantStatus;
import com.planted.repository.PlantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers(disabledWithoutDocker = true)
class PlantDetailHistoryTimelineIT {

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

    @Test
    void getPlant_includesWateringInHistoryEntries() throws Exception {
        Plant plant = new Plant();
        plant.setName("History timeline");
        plant.setStatus(PlantStatus.ACTIVE);
        plant = plantRepository.save(plant);
        long id = plant.getId();

        mockMvc.perform(post("/api/plants/" + id + "/waterings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wateredAt\":\"2026-04-10T15:30:00Z\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/plants/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyEntries[*].entryKind", hasItem("WATERING")))
                .andExpect(jsonPath("$.historyEntries[*].noteText", hasItem("Watered")));
    }
}
