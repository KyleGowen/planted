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
public class SpeciesIdStrategy implements PlantBioSectionStrategy {

    @Override
    public PlantBioSectionKey key() {
        return PlantBioSectionKey.SPECIES_ID;
    }

    @Override
    public String promptKey() {
        return "plant_species_id_v1";
    }

    @Override
    public Map<String, String> inputs(Plant plant, BioSectionContext ctx) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("plant_name", Optional.ofNullable(plant.getName()).orElse(""));
        vars.put("location", Optional.ofNullable(plant.getLocation()).orElse(""));
        return vars;
    }

    @Override
    public Map<String, Object> schema() {
        return BioSectionSchemas.speciesId();
    }
}
