package com.planted.worker;

import com.planted.entity.Plant;
import com.planted.entity.PlantAnalysis;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlantHistorySummaryProfileBuilderTest {

    @Test
    void formatGeographicLine_joinsCityStateCountry() {
        assertThat(PlantHistorySummaryProfileBuilder.formatGeographicLine("US", "TX", "Austin"))
                .isEqualTo("Austin, TX, US");
    }

    @Test
    void formatGeographicLine_returnsNullWhenEmpty() {
        assertThat(PlantHistorySummaryProfileBuilder.formatGeographicLine(null, "  ", null)).isNull();
    }

    @Test
    void buildPlantProfile_includesLocationGoalsGeoAndCareFields() {
        Plant plant = Plant.builder()
                .name("Fern")
                .location("East window")
                .goalsText("Bushier growth")
                .geoCity("Portland")
                .geoState("OR")
                .geoCountry("US")
                .build();

        PlantAnalysis care = PlantAnalysis.builder()
                .plantId(1L)
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .healthDiagnosis("Minor leaf drop, likely acclimation")
                .goalSuggestions("Rotate weekly for even growth")
                .pruningGuidance("Remove dead stems only when clearly dry")
                .lightNeeds("Bright indirect")
                .build();

        String text = PlantHistorySummaryProfileBuilder.buildPlantProfile(plant, care);

        assertThat(text).contains("Placement / location: East window");
        assertThat(text).contains("Owner goals: Bushier growth");
        assertThat(text).contains("Geographic context: Portland, OR, US");
        assertThat(text).contains("Latest completed care analysis snapshot");
        assertThat(text).contains("Health diagnosis: Minor leaf drop, likely acclimation");
        assertThat(text).contains("Goal suggestions (from analysis): Rotate weekly for even growth");
        assertThat(text).contains("Pruning guidance: Remove dead stems only when clearly dry");
        assertThat(text).doesNotContain("Light needs");
    }

    @Test
    void buildPlantProfile_prefersSplitPruningFieldsWhenPresent() {
        Plant plant = Plant.builder().name("P").build();
        PlantAnalysis care = PlantAnalysis.builder()
                .plantId(1L)
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .pruningActionSummary("Tip long stems after growing season if crowded.")
                .pruningGeneralGuidance("Species tolerates light shaping; avoid heavy cuts in winter.")
                .pruningGuidance("legacy should not appear when splits exist")
                .build();

        String text = PlantHistorySummaryProfileBuilder.buildPlantProfile(plant, care);

        assertThat(text).contains("Pruning action summary: Tip long stems after growing season if crowded.");
        assertThat(text).contains("Pruning (species guidance): Species tolerates light shaping");
        assertThat(text).doesNotContain("legacy should not appear");
    }

    @Test
    void buildPlantProfile_omitsCareHeaderWhenSnapshotWouldBeEmpty() {
        Plant plant = Plant.builder().name("X").build();
        PlantAnalysis emptyCare = PlantAnalysis.builder()
                .plantId(1L)
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .status(PlantAnalysis.AnalysisStatus.COMPLETED)
                .build();

        String text = PlantHistorySummaryProfileBuilder.buildPlantProfile(plant, emptyCare);

        assertThat(text).doesNotContain("Latest completed care analysis snapshot");
    }

    @Test
    void buildPlantProfile_worksWithNullCareAnalysis() {
        Plant plant = Plant.builder()
                .location("Kitchen")
                .build();

        String text = PlantHistorySummaryProfileBuilder.buildPlantProfile(plant, null);

        assertThat(text).contains("Placement / location: Kitchen");
        assertThat(text).doesNotContain("Latest completed care analysis snapshot");
    }
}
