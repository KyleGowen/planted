package com.planted.mapper;

import com.planted.entity.PlantAnalysis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlantMapperSpeciesOverviewTest {

    @Test
    void prefersSpeciesOverviewWhenPresent() {
        PlantAnalysis analysis = PlantAnalysis.builder()
                .plantId(1L)
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .speciesOverview("First para.\n\nSecond para.")
                .interestingFactsJson(List.of("legacy"))
                .build();
        assertEquals("First para.\n\nSecond para.", PlantMapper.resolveSpeciesOverview(analysis));
    }

    @Test
    void fallsBackToLegacyInterestingFactsJoined() {
        PlantAnalysis analysis = PlantAnalysis.builder()
                .plantId(1L)
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .interestingFactsJson(List.of("A", "B"))
                .build();
        assertEquals("A\n\nB", PlantMapper.resolveSpeciesOverview(analysis));
    }

    @Test
    void blankOverviewUsesLegacy() {
        PlantAnalysis analysis = PlantAnalysis.builder()
                .plantId(1L)
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .speciesOverview("   ")
                .interestingFactsJson(List.of("Legacy only"))
                .build();
        assertEquals("Legacy only", PlantMapper.resolveSpeciesOverview(analysis));
    }

    @Test
    void filtersBlankLegacyStrings() {
        PlantAnalysis analysis = PlantAnalysis.builder()
                .plantId(1L)
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .interestingFactsJson(List.of("A", "", "  ", "B"))
                .build();
        assertEquals("A\n\nB", PlantMapper.resolveSpeciesOverview(analysis));
    }

    @Test
    void nullAnalysisReturnsNull() {
        assertNull(PlantMapper.resolveSpeciesOverview(null));
    }

    @Test
    void syntheticOverviewFromStructuredFieldsWhenNoModelNarrative() {
        PlantAnalysis analysis = PlantAnalysis.builder()
                .plantId(1L)
                .analysisType(PlantAnalysis.AnalysisType.REGISTRATION)
                .scientificName("Schefflera arboricola")
                .className("Magnoliopsida")
                .nativeRegionsJson(List.of("Taiwan", "Hainan"))
                .lightNeeds("Bright indirect light.")
                .placementGuidance("Avoid cold drafts.")
                .build();
        String overview = PlantMapper.resolveSpeciesOverview(analysis);
        assertTrue(overview.contains("Schefflera arboricola"));
        assertTrue(overview.contains("Taiwan"));
        assertTrue(overview.contains("Bright indirect"));
    }
}
