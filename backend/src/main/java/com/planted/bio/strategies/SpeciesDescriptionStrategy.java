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
public class SpeciesDescriptionStrategy implements PlantBioSectionStrategy {

    @Override
    public PlantBioSectionKey key() {
        return PlantBioSectionKey.SPECIES_DESCRIPTION;
    }

    @Override
    public String promptKey() {
        return "plant_species_description_v1";
    }

    @Override
    public Map<String, String> inputs(Plant plant, BioSectionContext ctx) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("species_name", Optional.ofNullable(ctx.speciesName()).orElse(""));
        vars.put("taxonomic_family", Optional.ofNullable(ctx.taxonomicFamily()).orElse(""));
        vars.put("native_regions", Optional.ofNullable(ctx.nativeRegions()).orElse(""));
        vars.put("goals_text", Optional.ofNullable(ctx.goalsText()).orElse(""));
        vars.put("notes_text", Optional.ofNullable(ctx.notesText()).orElse(""));
        return vars;
    }

    @Override
    public Map<String, Object> schema() {
        return BioSectionSchemas.speciesDescription();
    }
}
