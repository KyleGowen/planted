package com.planted.bio.strategies;

import com.planted.bio.BioSectionContext;
import com.planted.entity.Plant;
import com.planted.entity.PlantGrowingContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the OWNER CERTAINTY plumbing: the four bio strategies that previously
 * never received owner free-text ({@code goals_text}) must now thread it into
 * their prompt variables so the V43 prompts can honor owner-asserted species,
 * native range, and care-routine facts.
 */
class BioStrategyGoalsTextTest {

    private static final String GOALS = "I want it to bloom; native to Madagascar";

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
                GOALS);
    }

    @Test
    void speciesDescriptionStrategy_passesGoalsText() {
        Map<String, String> vars = new SpeciesDescriptionStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
    }

    @Test
    void waterCareStrategy_passesGoalsText() {
        Map<String, String> vars = new WaterCareStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
    }

    @Test
    void fertilizerCareStrategy_passesGoalsText() {
        Map<String, String> vars = new FertilizerCareStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
    }

    @Test
    void pruningCareStrategy_passesGoalsText() {
        Map<String, String> vars = new PruningCareStrategy().inputs(plant(), ctx());
        assertThat(vars).containsEntry("goals_text", GOALS);
    }

    @Test
    void goalsText_defaultsToEmptyStringWhenContextIsEmpty() {
        Map<String, String> vars = new WaterCareStrategy().inputs(plant(), BioSectionContext.empty());
        assertThat(vars).containsEntry("goals_text", "");
    }
}
