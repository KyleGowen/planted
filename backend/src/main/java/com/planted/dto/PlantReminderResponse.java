package com.planted.dto;

public record PlantReminderResponse(
        Long plantId,
        String plantName,
        String speciesLabel,
        PlantImageDto illustratedImage,
        boolean isDue,
        boolean isOverdue,
        String instruction
) {}
