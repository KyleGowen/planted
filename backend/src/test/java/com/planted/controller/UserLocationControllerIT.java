package com.planted.controller;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserLocationControllerIT {

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

    @Test
    @Order(1)
    void getLocation_initiallyNull() throws Exception {
        mockMvc.perform(get("/api/user/location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(nullValue()));
    }

    @Test
    @Order(2)
    void putThenGet_roundTrip() throws Exception {
        mockMvc.perform(put("/api/user/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"123 Oak St, Austin, TX\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("123 Oak St, Austin, TX"));

        mockMvc.perform(get("/api/user/location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("123 Oak St, Austin, TX"));
    }

    @Test
    @Order(3)
    void putNull_clearsAddress() throws Exception {
        mockMvc.perform(put("/api/user/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"Somewhere\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/user/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(nullValue()));

        mockMvc.perform(get("/api/user/location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(nullValue()));
    }

    @Test
    @Order(4)
    void putOversizedAddress_returnsBadRequest() throws Exception {
        String tooLong = "x".repeat(1001);
        mockMvc.perform(put("/api/user/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
