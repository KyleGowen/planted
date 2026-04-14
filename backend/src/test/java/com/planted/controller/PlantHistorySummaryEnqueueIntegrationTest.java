package com.planted.controller;

import com.planted.entity.Plant;
import com.planted.entity.PlantHistoryEntry;
import com.planted.entity.PlantStatus;
import com.planted.queue.PlantJobPublisher;
import com.planted.repository.PlantHistoryEntryRepository;
import com.planted.repository.PlantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers(disabledWithoutDocker = true)
class PlantHistorySummaryEnqueueIntegrationTest {

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
    private PlantHistoryEntryRepository historyEntryRepository;

    @MockBean
    private PlantJobPublisher jobPublisher;

    @Test
    void postHistorySummary_returnsAcceptedAndEnqueuesJob() throws Exception {
        Plant plant = new Plant();
        plant.setName("Test plant");
        plant.setStatus(PlantStatus.ACTIVE);
        plant = plantRepository.save(plant);

        historyEntryRepository.save(PlantHistoryEntry.builder()
                .plantId(plant.getId())
                .noteText("Journal line for summary")
                .build());

        mockMvc.perform(post("/api/plants/" + plant.getId() + "/history/summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.plantId").value(plant.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(jobPublisher).publish(any());
    }
}
