package com.planted.dto;

import java.time.OffsetDateTime;

public record ReminderStateDto(
        boolean wateringDue,
        boolean wateringOverdue,
        boolean fertilizerDue,
        boolean pruningDue,
        boolean healthAttentionNeeded,
        boolean goalAttentionNeeded,
        String nextWateringInstruction,
        String nextFertilizerInstruction,
        String nextPruningInstruction,
        OffsetDateTime lastComputedAt
) {}
