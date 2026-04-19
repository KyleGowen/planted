package com.planted.service;

import com.planted.config.PlantedWeatherProperties;
import com.planted.dto.PlantImageDto;
import com.planted.dto.PlantReminderResponse;
import com.planted.entity.*;
import com.planted.mapper.PlantMapper;
import com.planted.repository.*;
import com.planted.weather.WeatherService;
import com.planted.weather.WeatherSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlantReminderService {

    private final PlantRepository plantRepository;
    private final PlantReminderStateRepository reminderStateRepository;
    private final PlantImageRepository imageRepository;
    private final PlantAnalysisRepository analysisRepository;
    private final PlantWateringEventRepository wateringEventRepository;
    private final PlantFertilizerEventRepository fertilizerEventRepository;
    private final PlantPruneEventRepository pruneEventRepository;
    private final PlantBioSectionRepository bioSectionRepository;
    private final PlantMapper plantMapper;
    private final WeatherService weatherService;
    private final PlantedWeatherProperties weatherProperties;

    @Transactional(readOnly = true)
    public List<PlantReminderResponse> getWateringReminders() {
        return plantRepository.findAllActive().stream()
                .map(plant -> {
                    PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElse(null);
                    boolean due = state != null && state.isWateringDue();
                    boolean overdue = state != null && state.isWateringOverdue();
                    String instruction = state != null ? state.getNextWateringInstruction() : null;
                    PlantImageDto img = getIllustratedImageDto(plant.getId());
                    return new PlantReminderResponse(
                            plant.getId(), plant.getName(), plant.getSpeciesLabel(), img,
                            due, overdue, instruction);
                })
                .filter(r -> r.isDue() || r.isOverdue())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlantReminderResponse> getFertilizerReminders() {
        return plantRepository.findAllActive().stream()
                .map(plant -> {
                    PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElse(null);
                    boolean due = state != null && state.isFertilizerDue();
                    String instruction = state != null ? state.getNextFertilizerInstruction() : null;
                    PlantImageDto img = getIllustratedImageDto(plant.getId());
                    return new PlantReminderResponse(
                            plant.getId(), plant.getName(), plant.getSpeciesLabel(), img,
                            due, false, instruction);
                })
                .filter(PlantReminderResponse::isDue)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlantReminderResponse> getPruningReminders() {
        return plantRepository.findAllActive().stream()
                .map(plant -> {
                    PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElse(null);
                    boolean due = state != null && state.isPruningDue();
                    String instruction = state != null ? state.getNextPruningInstruction() : null;
                    PlantImageDto img = getIllustratedImageDto(plant.getId());
                    return new PlantReminderResponse(
                            plant.getId(), plant.getName(), plant.getSpeciesLabel(), img,
                            due, false, instruction);
                })
                .filter(PlantReminderResponse::isDue)
                .toList();
    }

    /**
     * Recompute reminder state from persisted care history and latest analysis.
     * Called by the PLANT_REMINDER_RECOMPUTE job processor.
     * Outdoor plants with coordinates may enrich copy from Open-Meteo (no blocking on HTTP request threads).
     */
    @Transactional
    public void recomputeReminderState(Long plantId) {
        Plant plant = plantRepository.findById(plantId).orElse(null);

        PlantAnalysis analysis = analysisRepository
                .findFirstByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(
                        plantId, PlantAnalysis.AnalysisType.REGISTRATION)
                .filter(a -> a.getStatus() == PlantAnalysis.AnalysisStatus.COMPLETED)
                .orElse(null);

        Optional<PlantWateringEvent> lastWatering = wateringEventRepository
                .findFirstByPlantIdOrderByWateredAtDesc(plantId);
        Optional<PlantFertilizerEvent> lastFertilizer = fertilizerEventRepository
                .findFirstByPlantIdOrderByFertilizedAtDesc(plantId);
        Optional<PlantPruneEvent> lastPrune = pruneEventRepository
                .findFirstByPlantIdOrderByPrunedAtDesc(plantId);

        OffsetDateTime now = OffsetDateTime.now();

        boolean wateringDue = false;
        boolean wateringOverdue = false;
        boolean fertilizerDue = false;
        boolean pruningDue = false;
        String nextWateringInstruction = null;
        String nextFertilizerInstruction = null;
        String nextPruningInstruction = null;
        String weatherCareNote = null;

        if (analysis != null) {
            // Watering due check
            String wateringFrequency = analysis.getWateringFrequency();
            if (wateringFrequency != null && lastWatering.isPresent()) {
                long daysSinceWatering = ChronoUnit.DAYS.between(
                        lastWatering.get().getWateredAt(), now);
                long frequencyDays = parseFrequencyDays(wateringFrequency);
                if (frequencyDays > 0) {
                    wateringDue = daysSinceWatering >= frequencyDays;
                    wateringOverdue = daysSinceWatering >= frequencyDays + 2;
                    long daysUntil = frequencyDays - daysSinceWatering;
                    if (wateringOverdue) {
                        nextWateringInstruction = String.format(
                                "Overdue! %s was due %d days ago. Water now with %s.",
                                "Watering", Math.abs(daysUntil),
                                analysis.getWateringAmount() != null ? analysis.getWateringAmount() : "the recommended amount");
                    } else if (wateringDue) {
                        nextWateringInstruction = String.format(
                                "Time to water today. Use %s.",
                                analysis.getWateringAmount() != null ? analysis.getWateringAmount() : "the recommended amount");
                    } else {
                        nextWateringInstruction = String.format(
                                "Water in %d day%s. Use %s.",
                                daysUntil, daysUntil == 1 ? "" : "s",
                                analysis.getWateringAmount() != null ? analysis.getWateringAmount() : "the recommended amount");
                    }
                }
            } else if (lastWatering.isEmpty() && wateringFrequency != null) {
                wateringDue = true;
                nextWateringInstruction = "No watering history recorded. Check if watering is needed.";
            }
            // Fallback: frequency missing but prose guidance exists — show guidance so the row still appears.
            if (nextWateringInstruction == null) {
                String wateringProse = firstNonBlank(analysis.getWateringGuidance(), null, null);
                if (wateringProse != null) {
                    nextWateringInstruction = wateringProse;
                }
            }

            // Fertilizer due check (simple: every ~30 days if frequency present)
            String fertFrequency = analysis.getFertilizerFrequency();
            if (fertFrequency != null && lastFertilizer.isPresent()) {
                long daysSinceFertilizer = ChronoUnit.DAYS.between(
                        lastFertilizer.get().getFertilizedAt(), now);
                long fertDays = parseFrequencyDays(fertFrequency);
                if (fertDays > 0) {
                    fertilizerDue = daysSinceFertilizer >= fertDays;
                    if (fertilizerDue) {
                        nextFertilizerInstruction = String.format(
                                "Apply %s fertilizer today.",
                                analysis.getFertilizerType() != null ? analysis.getFertilizerType() : "balanced");
                    } else {
                        long daysUntilFert = fertDays - daysSinceFertilizer;
                        nextFertilizerInstruction = String.format(
                                "Next fertilizer in %d day%s.", daysUntilFert, daysUntilFert == 1 ? "" : "s");
                    }
                }
            } else if (lastFertilizer.isEmpty() && fertFrequency != null) {
                // No history — suggest based on season
                nextFertilizerInstruction = "No fertilizer history. Apply " +
                        (analysis.getFertilizerType() != null ? analysis.getFertilizerType() : "balanced") +
                        " fertilizer if it's the growing season.";
            }
            // Fallback: frequency missing but prose guidance exists — show guidance so the row still appears.
            if (nextFertilizerInstruction == null) {
                String fertProse = firstNonBlank(analysis.getFertilizerGuidance(), null, null);
                if (fertProse != null) {
                    nextFertilizerInstruction = fertProse;
                }
            }

            // Pruning: if there's guidance and no recent prune, flag as potentially due
            String pruningPrimary = firstNonBlank(
                    analysis.getPruningActionSummary(),
                    analysis.getPruningGuidance(),
                    analysis.getPruningGeneralGuidance());
            if (pruningPrimary != null) {
                boolean noPruneHistory = lastPrune.isEmpty();
                boolean prunedLongAgo = lastPrune
                        .map(e -> ChronoUnit.DAYS.between(e.getPrunedAt(), now) > 180)
                        .orElse(false);
                pruningDue = noPruneHistory || prunedLongAgo;
                nextPruningInstruction = pruningPrimary;
            }
        }

        if (plant != null
                && plant.getGrowingContext() == PlantGrowingContext.OUTDOOR
                && plant.getLatitude() != null
                && plant.getLongitude() != null) {
            Optional<WeatherSnapshot> wx = weatherService.fetchSnapshot(
                    plant.getLatitude(), plant.getLongitude());
            if (wx.isPresent()) {
                WeatherSnapshot s = wx.get();
                boolean softenedWateringDue = false;
                if (s.pastWeekPrecipitationMm() >= weatherProperties.getRecentRainWeekThresholdMm()
                        && wateringDue
                        && !wateringOverdue) {
                    wateringDue = false;
                    wateringOverdue = false;
                    softenedWateringDue = true;
                    nextWateringInstruction =
                            "Your schedule suggests watering, but recent rain may mean the soil is still moist—"
                                    + "check soil before watering.";
                }
                weatherCareNote = buildWeatherCareNote(s);
                nextWateringInstruction = softenedWateringDue
                        ? nextWateringInstruction
                        : appendWateringWeather(nextWateringInstruction, s);
                nextFertilizerInstruction = appendFertilizerWeather(nextFertilizerInstruction, s, fertilizerDue);
                nextPruningInstruction = appendPruningWeather(nextPruningInstruction, s);
            }
        }

        // Upsert reminder state
        PlantReminderState state = reminderStateRepository.findByPlantId(plantId)
                .orElse(PlantReminderState.builder().plantId(plantId).build());

        state.setWateringDue(wateringDue);
        state.setWateringOverdue(wateringOverdue);
        state.setFertilizerDue(fertilizerDue);
        state.setPruningDue(pruningDue);
        state.setNextWateringInstruction(nextWateringInstruction);
        state.setNextFertilizerInstruction(nextFertilizerInstruction);
        state.setNextPruningInstruction(nextPruningInstruction);
        state.setWeatherCareNote(weatherCareNote);
        applyBioAttentionFlags(plantId, state);

        reminderStateRepository.save(state);
        log.info("Reminder state recomputed for plant {}", plantId);
    }

    /**
     * Refresh the health / light / placement attention booleans (and reason strings) on
     * {@link PlantReminderState} from the latest completed bio-section content. Invoked
     * after a bio section completes so the plant-list + screensaver icon row can illuminate
     * without the UI re-reading bio JSON. Safe to call for a plant that has no bio
     * sections yet — flags stay at their current values (default false) when nothing is
     * available, and an attention=false section always clears both the flag and the
     * reason so a resolved issue doesn't leave a stale tooltip behind.
     */
    @Transactional
    public void syncBioAttention(Long plantId) {
        PlantReminderState state = reminderStateRepository.findByPlantId(plantId)
                .orElse(PlantReminderState.builder().plantId(plantId).build());
        applyBioAttentionFlags(plantId, state);
        reminderStateRepository.save(state);
    }

    /**
     * Reads the three attention-bearing bio sections and writes their flags/reasons onto the
     * given (possibly transient) reminder-state row. Only COMPLETED sections with a
     * non-null {@code contentJsonb} are consulted; anything else leaves the corresponding
     * flag untouched at its current value. The rationale: during re-analysis, a section
     * flips to PROCESSING and we want the prior flag to persist until the new answer
     * arrives so the UI doesn't briefly un-illuminate.
     */
    private void applyBioAttentionFlags(Long plantId, PlantReminderState state) {
        applyOne(plantId, PlantBioSectionKey.HEALTH_ASSESSMENT,
                state::setHealthAttentionNeeded, state::setHealthAttentionReason);
        applyOne(plantId, PlantBioSectionKey.LIGHT_CARE,
                state::setLightAttentionNeeded, state::setLightAttentionReason);
        applyOne(plantId, PlantBioSectionKey.PLACEMENT_CARE,
                state::setPlacementAttentionNeeded, state::setPlacementAttentionReason);
    }

    private void applyOne(Long plantId,
                          PlantBioSectionKey key,
                          java.util.function.Consumer<Boolean> flagSetter,
                          java.util.function.Consumer<String> reasonSetter) {
        PlantBioSection row = bioSectionRepository
                .findByPlantIdAndSectionKey(plantId, key)
                .orElse(null);
        if (row == null
                || row.getStatus() != PlantBioSection.Status.COMPLETED
                || row.getContentJsonb() == null) {
            return;
        }
        Map<String, Object> content = row.getContentJsonb();
        Object attentionNeeded = content.get("attentionNeeded");
        Object attentionReason = content.get("attentionReason");
        boolean flag = attentionNeeded instanceof Boolean b && b;
        flagSetter.accept(flag);
        reasonSetter.accept(flag ? asReasonString(attentionReason) : null);
    }

    private static String asReasonString(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private String buildWeatherCareNote(WeatherSnapshot s) {
        List<String> parts = new ArrayList<>();
        if (s.pastWeekPrecipitationMm() >= weatherProperties.getRecentRainWeekThresholdMm()) {
            parts.add(String.format(
                    "About %.0f mm rain in the past week—check soil moisture before watering outdoor plants.",
                    s.pastWeekPrecipitationMm()));
        }
        if (s.forecastPrecipNextTwoDaysMm() >= weatherProperties.getForecastRainThresholdMm()) {
            parts.add("Rain is expected soon; outdoor containers may need less water.");
        }
        if (s.likelyHeatStress()) {
            parts.add("Recent heat can stress plants—provide shade if possible and avoid heavy pruning or fertilizer until it cools.");
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" ", parts);
    }

    private String appendWateringWeather(String base, WeatherSnapshot s) {
        if (base == null || base.isBlank()) {
            return base;
        }
        if (s.pastWeekPrecipitationMm() >= weatherProperties.getRecentRainWeekThresholdMm()) {
            return base + " Recent rain may mean you can wait if the soil is still moist.";
        }
        if (s.forecastPrecipNextTwoDaysMm() >= weatherProperties.getForecastRainThresholdMm()) {
            return base + " Rain is forecast—recheck before watering.";
        }
        return base;
    }

    private String appendFertilizerWeather(String base, WeatherSnapshot s, boolean fertilizerDue) {
        if (base == null || base.isBlank() || !fertilizerDue) {
            return base;
        }
        if (s.likelyHeatStress()) {
            return base + " Consider delaying fertilizer until heat eases.";
        }
        return base;
    }

    private String appendPruningWeather(String base, WeatherSnapshot s) {
        if (base == null || base.isBlank()) {
            return base;
        }
        if (s.likelyHeatStress()) {
            return base + " Avoid heavy pruning during heat stress.";
        }
        return base;
    }

    private static String firstNonBlank(String a, String b, String c) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        if (c != null && !c.isBlank()) {
            return c;
        }
        return null;
    }

    private PlantImageDto getIllustratedImageDto(Long plantId) {
        return imageRepository
                .findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(plantId, PlantImage.ImageType.ILLUSTRATED)
                .map(plantMapper::toImageDto)
                .orElse(null);
    }

    /**
     * Parse a human-readable frequency like "every 7 days", "weekly", "twice a week" into days.
     */
    private long parseFrequencyDays(String frequency) {
        if (frequency == null) return 0;
        String lower = frequency.toLowerCase();
        if (lower.contains("daily") || lower.contains("every day") || lower.contains("every 1 day")) return 1;
        if (lower.contains("twice a week") || lower.contains("2x per week")) return 3;
        if (lower.contains("weekly") || lower.contains("every week") || lower.contains("every 7 day")) return 7;
        if (lower.contains("every 10 day")) return 10;
        if (lower.contains("every 14 day") || lower.contains("bi-weekly") || lower.contains("biweekly")) return 14;
        if (lower.contains("every 2 week")) return 14;
        if (lower.contains("monthly") || lower.contains("every month") || lower.contains("every 30 day")) return 30;
        if (lower.contains("every 6 week")) return 42;
        if (lower.contains("every 2 month")) return 60;
        // Try to extract a number
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*day").matcher(lower);
        if (m.find()) return Long.parseLong(m.group(1));
        return 7; // default to weekly if unparseable
    }
}
