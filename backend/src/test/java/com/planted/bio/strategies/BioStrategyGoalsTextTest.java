package com.planted.bio.strategies;

import com.planted.bio.BioSectionContext;
import com.planted.entity.Plant;
import com.planted.entity.PlantGrowingContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the OWNER CERTAINTY plumbing: every bio strategy that reasons about
 * owner-asserted facts must thread {@code goals_text} and {@code notes_text}
 * into its prompt variables so the V44 prompts can honor owner-asserted
 * species, native range, and care-routine facts — whether those assertions
 * live in goals_text (from the plant's goals field) or notes_text (from
 * journal notes).
 */
class BioStrategyGoalsTextTest {

    private static final String GOALS = "I want it to bloom; native to Madagascar";
    private static final String NOTES = "2026-04-19: Correcting earlier ID — this is actually Sansevieria cylindrica, not Sansevieria trifasciata.";

    private static Plant plant() {
        Plant plant = new Plant();
        plant.setId(1L);
        plant.setLocation("south-facing window");
        plant.setGoalsText(GOALS);
        plant.setGrowingContext(PlantGrowingContext.INDOOR);
        return plant;
    }

    private static BioSectionContext ctx() {
        return new BioSectionContext(
                "Ficus lyrata",
                "Moraceae",
                "West Africa",
                "Austin, TX",
                "",
                GOALS,
                NOTES);
    }

    @Test
    void speciesIdStrategy_passesGoalsAndNotes() {
        Map<String, String> vars = new SpeciesIdStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
        assertThat(vars).containsEntry("notes_text", NOTES);
    }

    @Test
    void speciesDescriptionStrategy_passesGoalsAndNotes() {
        Map<String, String> vars = new SpeciesDescriptionStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
        assertThat(vars).containsEntry("notes_text", NOTES);
    }

    @Test
    void waterCareStrategy_passesGoalsAndNotes() {
        Map<String, String> vars = new WaterCareStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
        assertThat(vars).containsEntry("notes_text", NOTES);
    }

    @Test
    void fertilizerCareStrategy_passesGoalsAndNotes() {
        Map<String, String> vars = new FertilizerCareStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
        assertThat(vars).containsEntry("notes_text", NOTES);
    }

    @Test
    void pruningCareStrategy_passesGoalsAndNotes() {
        Map<String, String> vars = new PruningCareStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
        assertThat(vars).containsEntry("notes_text", NOTES);
    }

    @Test
    void placementCareStrategy_passesGoalsAndNotes() {
        Map<String, String> vars = new PlacementCareStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
        assertThat(vars).containsEntry("notes_text", NOTES);
    }

    @Test
    void lightCareStrategy_passesGoalsAndNotes() {
        Map<String, String> vars = new LightCareStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
        assertThat(vars).containsEntry("notes_text", NOTES);
    }

    @Test
    void healthAssessmentStrategy_passesGoalsAndNotes() {
        Map<String, String> vars = new HealthAssessmentStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
        assertThat(vars).containsEntry("notes_text", NOTES);
    }

    @Test
    void ownerFreeText_defaultsToEmptyStringsWhenContextIsEmpty() {
        BioSectionContext empty = BioSectionContext.empty();
        Plant bare = new Plant();
        bare.setId(2L);

        assertThat(new SpeciesIdStrategy().inputs(bare, empty))
                .containsEntry("goals_text", "")
                .containsEntry("notes_text", "");
        assertThat(new WaterCareStrategy().inputs(bare, empty))
                .containsEntry("goals_text", "")
                .containsEntry("notes_text", "");
        assertThat(new HealthAssessmentStrategy().inputs(bare, empty))
                .containsEntry("goals_text", "")
                .containsEntry("notes_text", "");
    }
}
