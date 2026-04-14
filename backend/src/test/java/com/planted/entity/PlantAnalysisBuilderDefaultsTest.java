package com.planted.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlantAnalysisBuilderDefaultsTest {

    @Test
    void builderAppliesDefaultPendingStatus() {
        PlantAnalysis analysis = PlantAnalysis.builder()
                .plantId(1L)
                .analysisType(PlantAnalysis.AnalysisType.INFO_PANEL)
                .build();

        assertNotNull(analysis.getStatus());
        assertEquals(PlantAnalysis.AnalysisStatus.PENDING, analysis.getStatus());
    }
}
