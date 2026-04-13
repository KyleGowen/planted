package com.planted.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record RecordWateringRequest(
        @NotNull OffsetDateTime wateredAt,
        String notes
) {}
