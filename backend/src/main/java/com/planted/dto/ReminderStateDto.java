package com.planted.dto;

import java.time.OffsetDateTime;

public record ReminderStateDto(
        boolean wateringDue,
        boolean wateringOverdue,
        boolean fertilizerDue,
        boolean pruningDue,
        boolean healthAttentionNeeded,
        boolean lightAttentionNeeded,
        boolean placementAttentionNeeded,
        String nextWateringInstruction,
        String nextFertilizerInstruction,
        String nextPruningInstruction,
        String healthAttentionReason,
        String lightAttentionReason,
        String placementAttentionReason,
        String weatherCareNote,
        OffsetDateTime lastComputedAt
) {}
