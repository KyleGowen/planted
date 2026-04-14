package com.planted.controller;

import org.junit.jupiter.api.Test;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerNonUniqueMessageTest {

    @Test
    void dataAccessHandler_surfacesRestartHintForNonUniqueResult() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        IncorrectResultSizeDataAccessException ex = new IncorrectResultSizeDataAccessException(1, 5);
        ResponseEntity<Map<String, Object>> res = handler.handleDataAccess(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat((String) res.getBody().get("error")).contains("Stop the backend");
    }
}
