package com.planted.controller;

import com.planted.dto.UpdatePlantGrowingRequest;
import com.planted.service.PlantCommandService;
import com.planted.service.PlantQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlantController.class)
class PlantControllerGrowingPatchTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlantCommandService commandService;

    @MockBean
    private PlantQueryService queryService;

    @Test
    void updatePlantGrowing_returns204() throws Exception {
        mockMvc.perform(patch("/api/plants/5/growing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"growingContext\":\"OUTDOOR\"}"))
                .andExpect(status().isNoContent());

        verify(commandService).updatePlantGrowing(eq(5L), any(UpdatePlantGrowingRequest.class));
    }
}
