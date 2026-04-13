package com.planted.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record RecordFertilizerRequest(
        @NotNull OffsetDateTime fertilizedAt,
        String fertilizerType,
        String notes
) {}
