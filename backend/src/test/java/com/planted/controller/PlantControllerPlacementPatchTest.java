package com.planted.controller;

import com.planted.dto.RequestReanalysisResponse;
import com.planted.service.PlantCommandService;
import com.planted.service.PlantQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlantController.class)
class PlantControllerPlacementPatchTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlantCommandService commandService;

    @MockBean
    private PlantQueryService queryService;

    @Test
    void updatePlantPlacement_returns202AndBody() throws Exception {
        OffsetDateTime created = OffsetDateTime.parse("2026-01-02T12:00:00Z");
        when(commandService.updatePlantPlacement(eq(5L), eq("East window")))
                .thenReturn(new RequestReanalysisResponse(99L, 5L, "PENDING", created));

        mockMvc.perform(patch("/api/plants/5/placement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"East window\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value(99))
                .andExpect(jsonPath("$.plantId").value(5))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(commandService).updatePlantPlacement(eq(5L), eq("East window"));
    }
}
