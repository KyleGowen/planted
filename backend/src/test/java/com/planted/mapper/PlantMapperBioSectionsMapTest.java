package com.planted.mapper;

import com.planted.dto.BioSectionDto;
import com.planted.entity.PlantAnalysis;
import com.planted.entity.PlantBioSection;
import com.planted.entity.PlantBioSectionKey;
import com.planted.repository.PlantImageRepository;
import com.planted.storage.ImageStorageService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the per-section map construction on {@link PlantMapper}. Primary
 * goals:
 * <ol>
 *   <li>Real {@link PlantBioSection} rows win over the legacy analysis.</li>
 *   <li>Missing sections are backfilled from {@code latestAnalysis} so existing
 *       plants keep rendering while the async refresh jobs populate the cache.</li>
 *   <li>The {@code isRefreshing} flag correctly reflects stale / in-progress
 *       rows so the frontend knows when to keep polling.</li>
 * </ol>
 */
class PlantMapperBioSectionsMapTest {

    private final PlantMapper mapper = new PlantMapper(
            (ImageStorageService) null, (PlantImageRepository) null);

    @Test
    void emptyList_fallsBackToLegacyAnalysisForEverySection() {
        PlantAnalysis legacy = legacyAnalysis();

        Map<String, BioSectionDto> map = mapper.toBioSectionsMap(Collections.emptyList(), legacy);

        assertThat(map).containsOnlyKeys(names(PlantBioSectionKey.values()));

        BioSectionDto water = map.get(PlantBioSectionKey.WATER_CARE.name());
        assertThat(water.status()).isEqualTo("COMPLETED");
        assertThat(water.content()).containsEntry("guidance", "Water once a week")
                .containsEntry("frequency", "Weekly");

        BioSectionDto desc = map.get(PlantBioSectionKey.SPECIES_DESCRIPTION.name());
        assertThat(desc.content()).containsEntry("overview", "A lovely fern.");

        // HISTORY_SUMMARY has no legacy equivalent and stays pending.
        BioSectionDto history = map.get(PlantBioSectionKey.HISTORY_SUMMARY.name());
        assertThat(history.status()).isEqualTo("PENDING");
        assertThat(history.content()).isNull();
    }

    @Test
    void realRowWinsOverLegacyFallback() {
        PlantAnalysis legacy = legacyAnalysis();
        PlantBioSection realWaterRow = PlantBioSection.builder()
                .plantId(1L)
                .sectionKey(PlantBioSectionKey.WATER_CARE)
                .status(PlantBioSection.Status.COMPLETED)
                .contentJsonb(Map.of("guidance", "Deep soak every 10 days"))
                .promptKey("plant_water_care_v1")
                .promptVersion(1)
                .generatedAt(OffsetDateTime.now())
                .build();

        Map<String, BioSectionDto> map = mapper.toBioSectionsMap(List.of(realWaterRow), legacy);

        BioSectionDto water = map.get(PlantBioSectionKey.WATER_CARE.name());
        assertThat(water.promptKey()).isEqualTo("plant_water_care_v1");
        assertThat(water.content()).containsEntry("guidance", "Deep soak every 10 days");
        assertThat(water.isRefreshing()).isFalse();

        // Other sections still come from the legacy fallback.
        assertThat(map.get(PlantBioSectionKey.SPECIES_DESCRIPTION.name()).content())
                .containsEntry("overview", "A lovely fern.");
    }

    @Test
    void processingRow_marksSectionAsRefreshing() {
        PlantBioSection processing = PlantBioSection.builder()
                .plantId(1L)
                .sectionKey(PlantBioSectionKey.WATER_CARE)
                .status(PlantBioSection.Status.PROCESSING)
                .generatedAt(null)
                .build();

        Map<String, BioSectionDto> map = mapper.toBioSectionsMap(List.of(processing), legacyAnalysis());

        BioSectionDto water = map.get(PlantBioSectionKey.WATER_CARE.name());
        assertThat(water.status()).isEqualTo("PROCESSING");
        assertThat(water.isRefreshing()).isTrue();
    }

    @Test
    void completedButExpired_marksSectionAsRefreshing() {
        // WATER_CARE TTL is 24h; use a generatedAt two days ago to force expiry.
        PlantBioSection expired = PlantBioSection.builder()
                .plantId(1L)
                .sectionKey(PlantBioSectionKey.WATER_CARE)
                .status(PlantBioSection.Status.COMPLETED)
                .contentJsonb(Map.of("guidance", "Weekly"))
                .generatedAt(OffsetDateTime.now().minusDays(2))
                .build();

        Map<String, BioSectionDto> map = mapper.toBioSectionsMap(List.of(expired), legacyAnalysis());

        BioSectionDto water = map.get(PlantBioSectionKey.WATER_CARE.name());
        assertThat(water.status()).isEqualTo("COMPLETED");
        assertThat(water.isRefreshing()).isTrue();
    }

    @Test
    void noLegacyAndNoRows_allSectionsPendingAndRefreshing() {
        Map<String, BioSectionDto> map = mapper.toBioSectionsMap(Collections.emptyList(), null);

        for (PlantBioSectionKey key : PlantBioSectionKey.values()) {
            BioSectionDto dto = map.get(key.name());
            assertThat(dto.status()).isEqualTo("PENDING");
            assertThat(dto.content()).isNull();
            assertThat(dto.isRefreshing()).isTrue();
        }
    }

    private static PlantAnalysis legacyAnalysis() {
        PlantAnalysis a = new PlantAnalysis();
        a.setId(1L);
        a.setStatus(PlantAnalysis.AnalysisStatus.COMPLETED);
        a.setAnalysisType(PlantAnalysis.AnalysisType.REGISTRATION);
        a.setGenus("Nephrolepis");
        a.setSpecies("exaltata");
        a.setTaxonomicFamily("Nephrolepidaceae");
        a.setClassName("Boston fern");
        a.setScientificName("Nephrolepis exaltata");
        a.setSpeciesOverview("A lovely fern.");
        a.setWateringGuidance("Water once a week");
        a.setWateringFrequency("Weekly");
        a.setLightNeeds("Bright indirect");
        a.setPlacementGuidance("Kitchen window");
        a.setFertilizerGuidance("Monthly balanced");
        a.setPruningActionSummary("Trim dead fronds");
        a.setHealthDiagnosis("Healthy overall");
        a.setCompletedAt(OffsetDateTime.now());
        a.setUsesJson(List.of("Air purification"));
        return a;
    }

    private static String[] names(PlantBioSectionKey[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) out[i] = values[i].name();
        return out;
    }

    @SuppressWarnings("unused")
    private LinkedHashMap<String, Object> empty() { return new LinkedHashMap<>(); }
}
