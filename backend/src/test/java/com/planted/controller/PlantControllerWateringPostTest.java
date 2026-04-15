package com.planted.controller;

import com.planted.dto.RecordWateringRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlantController.class)
class PlantControllerWateringPostTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlantCommandService commandService;

    @MockBean
    private PlantQueryService queryService;

    @Test
    void recordWatering_returnsCreated() throws Exception {
        String body = "{\"wateredAt\":\"2026-04-10T15:30:00Z\"}";

        mockMvc.perform(post("/api/plants/3/waterings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(commandService).recordWatering(eq(3L), any(RecordWateringRequest.class));
    }
}
