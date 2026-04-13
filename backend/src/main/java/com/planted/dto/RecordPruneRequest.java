package com.planted.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record RecordPruneRequest(
        @NotNull OffsetDateTime prunedAt,
        String notes
) {}
