package com.planted.dto;

public record PlantListItemResponse(
        Long id,
        String name,
        String genus,
        String species,
        String speciesLabel,
        String displayLabel,
        PlantImageDto illustratedImage,
        PlantImageDto originalImage,
        ReminderStateDto reminderState,
        String status,
        String analysisStatus,
        String scientificName,
        String speciesOverview
) {}
