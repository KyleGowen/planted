package com.planted.service;

import com.planted.entity.PlantWateringEvent;
import com.planted.repository.PlantFertilizerEventRepository;
import com.planted.repository.PlantPruneEventRepository;
import com.planted.repository.PlantWateringEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the {@code care_history} string for LLM prompts (registration, reanalysis, pruning).
 */
@Component
@RequiredArgsConstructor
public class CareHistoryFormatter {

    public static final int MAX_WATERING_DATES = 20;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final PlantWateringEventRepository wateringEventRepository;
    private final PlantFertilizerEventRepository fertilizerEventRepository;
    private final PlantPruneEventRepository pruneEventRepository;

    public String formatForLlm(Long plantId) {
        List<PlantWateringEvent> waterings = wateringEventRepository
                .findByPlantIdOrderByWateredAtDesc(plantId)
                .stream()
                .limit(MAX_WATERING_DATES)
                .toList();
        String wateringPart = waterings.isEmpty()
                ? "Never recorded"
                : waterings.stream()
                        .map(e -> e.getWateredAt().format(DATE_FMT))
                        .collect(Collectors.joining("; "));

        String lastFertilized = fertilizerEventRepository
                .findFirstByPlantIdOrderByFertilizedAtDesc(plantId)
                .map(e -> e.getFertilizedAt().format(DATE_FMT))
                .orElse("Never recorded");

        String lastPruned = pruneEventRepository
                .findFirstByPlantIdOrderByPrunedAtDesc(plantId)
                .map(e -> e.getPrunedAt().format(DATE_FMT))
                .orElse("Never recorded");

        return "Recent watering dates (newest first): " + wateringPart + ". "
                + "Last fertilized: " + lastFertilized + ". "
                + "Last pruned: " + lastPruned + ".";
    }
}
