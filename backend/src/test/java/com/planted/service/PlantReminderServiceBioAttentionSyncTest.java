package com.planted.service;

import com.planted.entity.Plant;
import com.planted.entity.PlantBioSection;
import com.planted.entity.PlantBioSectionKey;
import com.planted.entity.PlantGrowingContext;
import com.planted.entity.PlantReminderState;
import com.planted.entity.PlantStatus;
import com.planted.repository.PlantBioSectionRepository;
import com.planted.repository.PlantRepository;
import com.planted.repository.PlantReminderStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link PlantReminderService#syncBioAttention(Long)} behavior: the
 * attentionNeeded + attentionReason fields from the three attention-bearing bio
 * sections (HEALTH_ASSESSMENT, LIGHT_CARE, PLACEMENT_CARE) must land on the
 * plant's reminder-state row so the plant list + screensaver icon row can
 * illuminate without re-reading bio JSON.
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Testcontainers(disabledWithoutDocker = true)
class PlantReminderServiceBioAttentionSyncTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private PlantReminderService plantReminderService;
    @Autowired private PlantRepository plantRepository;
    @Autowired private PlantBioSectionRepository bioSectionRepository;
    @Autowired private PlantReminderStateRepository reminderStateRepository;

    @Test
    void syncBioAttention_allThreeSectionsFlagged_populatesFlagsAndReasons() {
        Plant plant = newPlant("All three flagged");
        saveBioSection(plant.getId(), PlantBioSectionKey.HEALTH_ASSESSMENT,
                attentionContent(true, "Yellowing lower leaves, likely overwatering"));
        saveBioSection(plant.getId(), PlantBioSectionKey.LIGHT_CARE,
                attentionContent(true, "Likely too dark for this species"));
        saveBioSection(plant.getId(), PlantBioSectionKey.PLACEMENT_CARE,
                attentionContent(true, "Likely outgrowing its pot"));

        plantReminderService.syncBioAttention(plant.getId());

        PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElseThrow();
        assertThat(state.isHealthAttentionNeeded()).isTrue();
        assertThat(state.getHealthAttentionReason()).isEqualTo("Yellowing lower leaves, likely overwatering");
        assertThat(state.isLightAttentionNeeded()).isTrue();
        assertThat(state.getLightAttentionReason()).isEqualTo("Likely too dark for this species");
        assertThat(state.isPlacementAttentionNeeded()).isTrue();
        assertThat(state.getPlacementAttentionReason()).isEqualTo("Likely outgrowing its pot");
    }

    @Test
    void syncBioAttention_noSectionsFlagged_allFalse_andReasonsNull() {
        Plant plant = newPlant("Healthy plant");
        saveBioSection(plant.getId(), PlantBioSectionKey.HEALTH_ASSESSMENT,
                attentionContent(false, ""));
        saveBioSection(plant.getId(), PlantBioSectionKey.LIGHT_CARE,
                attentionContent(false, ""));
        saveBioSection(plant.getId(), PlantBioSectionKey.PLACEMENT_CARE,
                attentionContent(false, ""));

        plantReminderService.syncBioAttention(plant.getId());

        PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElseThrow();
        assertThat(state.isHealthAttentionNeeded()).isFalse();
        assertThat(state.getHealthAttentionReason()).isNull();
        assertThat(state.isLightAttentionNeeded()).isFalse();
        assertThat(state.getLightAttentionReason()).isNull();
        assertThat(state.isPlacementAttentionNeeded()).isFalse();
        assertThat(state.getPlacementAttentionReason()).isNull();
    }

    @Test
    void syncBioAttention_mixedFlags_onlyFlaggedReasonsWritten() {
        Plant plant = newPlant("Mixed flags");
        saveBioSection(plant.getId(), PlantBioSectionKey.HEALTH_ASSESSMENT,
                attentionContent(false, ""));
        saveBioSection(plant.getId(), PlantBioSectionKey.LIGHT_CARE,
                attentionContent(true, "Bleached by direct afternoon sun"));
        saveBioSection(plant.getId(), PlantBioSectionKey.PLACEMENT_CARE,
                attentionContent(false, ""));

        plantReminderService.syncBioAttention(plant.getId());

        PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElseThrow();
        assertThat(state.isHealthAttentionNeeded()).isFalse();
        assertThat(state.getHealthAttentionReason()).isNull();
        assertThat(state.isLightAttentionNeeded()).isTrue();
        assertThat(state.getLightAttentionReason()).isEqualTo("Bleached by direct afternoon sun");
        assertThat(state.isPlacementAttentionNeeded()).isFalse();
        assertThat(state.getPlacementAttentionReason()).isNull();
    }

    @Test
    void syncBioAttention_pendingOrMissingSections_leavesPriorFlagsIntact() {
        // Ensure that while a bio section is mid-refresh (PROCESSING) or absent,
        // we don't clobber a previously-true flag — the UI shouldn't un-illuminate
        // between bio regenerations.
        Plant plant = newPlant("Prior flag kept");
        reminderStateRepository.save(PlantReminderState.builder()
                .plantId(plant.getId())
                .lightAttentionNeeded(true)
                .lightAttentionReason("Previously flagged as too dark")
                .build());

        PlantBioSection pending = PlantBioSection.builder()
                .plantId(plant.getId())
                .sectionKey(PlantBioSectionKey.LIGHT_CARE)
                .status(PlantBioSection.Status.PROCESSING)
                .generatedAt(null)
                .build();
        bioSectionRepository.save(pending);

        plantReminderService.syncBioAttention(plant.getId());

        PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElseThrow();
        assertThat(state.isLightAttentionNeeded()).isTrue();
        assertThat(state.getLightAttentionReason()).isEqualTo("Previously flagged as too dark");
    }

    @Test
    void syncBioAttention_blankReasonString_treatedAsNull() {
        Plant plant = newPlant("Blank reason");
        saveBioSection(plant.getId(), PlantBioSectionKey.HEALTH_ASSESSMENT,
                attentionContent(true, "   "));

        plantReminderService.syncBioAttention(plant.getId());

        PlantReminderState state = reminderStateRepository.findByPlantId(plant.getId()).orElseThrow();
        assertThat(state.isHealthAttentionNeeded()).isTrue();
        assertThat(state.getHealthAttentionReason()).isNull();
    }

    private Plant newPlant(String name) {
        return plantRepository.save(Plant.builder()
                .userId("default")
                .name(name)
                .status(PlantStatus.ACTIVE)
                .growingContext(PlantGrowingContext.INDOOR)
                .build());
    }

    private void saveBioSection(Long plantId, PlantBioSectionKey key, Map<String, Object> content) {
        PlantBioSection row = PlantBioSection.builder()
                .plantId(plantId)
                .sectionKey(key)
                .status(PlantBioSection.Status.COMPLETED)
                .contentJsonb(content)
                .generatedAt(OffsetDateTime.now())
                .build();
        bioSectionRepository.save(row);
    }

    private static Map<String, Object> attentionContent(boolean attentionNeeded, String attentionReason) {
        Map<String, Object> content = new HashMap<>();
        content.put("attentionNeeded", attentionNeeded);
        content.put("attentionReason", attentionReason);
        return content;
    }
}
