package com.planted.controller;

import com.planted.dto.RequestHistorySummaryResponse;
import com.planted.service.PlantCommandService;
import com.planted.service.PlantQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlantController.class)
class PlantControllerHistorySummaryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlantCommandService commandService;

    @MockBean
    private PlantQueryService queryService;

    @Test
    void requestHistorySummary_returnsAccepted() throws Exception {
        when(commandService.requestHistorySummary(eq(42L)))
                .thenReturn(new RequestHistorySummaryResponse(100L, 42L, "PENDING", "2026-04-13T10:00:00Z"));

        mockMvc.perform(post("/api/plants/42/history/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value(100))
                .andExpect(jsonPath("$.plantId").value(42))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-13T10:00:00Z"));
    }
}
