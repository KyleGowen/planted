package com.planted.bio.strategies;

import com.planted.bio.BioSectionContext;
import com.planted.bio.BioSectionSchemas;
import com.planted.bio.PlantBioSectionStrategy;
import com.planted.entity.Plant;
import com.planted.entity.PlantBioSectionKey;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class HealthAssessmentStrategy implements PlantBioSectionStrategy {

    @Override
    public PlantBioSectionKey key() {
        return PlantBioSectionKey.HEALTH_ASSESSMENT;
    }

    @Override
    public String promptKey() {
        return "plant_health_assessment_v1";
    }

    @Override
    public Map<String, String> inputs(Plant plant, BioSectionContext ctx) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("species_name", Optional.ofNullable(ctx.speciesName()).orElse(""));
        vars.put("location", Optional.ofNullable(plant.getLocation()).orElse(""));
        return vars;
    }

    @Override
    public Map<String, Object> schema() {
        return BioSectionSchemas.healthAssessment();
    }
}
