package com.planted.bio;

import com.planted.entity.Plant;
import com.planted.entity.PlantBioSectionKey;

import java.util.Map;

/**
 * Encapsulates everything specific to one {@link PlantBioSectionKey}: which
 * prompt key it uses, what inputs it collects from the plant (+ related
 * context), how to fingerprint those inputs, and how to shape the request
 * schema. The processor is intentionally generic — section-specific behavior
 * lives here.
 */
public interface PlantBioSectionStrategy {

    PlantBioSectionKey key();

    /** Prompt key looked up in {@code llm_prompts}. */
    String promptKey();

    /**
     * Assemble template variables for the prompt AND fingerprint inputs (same
     * set). The returned map is used both for handlebars substitution and for
     * hashing.
     */
    Map<String, String> inputs(Plant plant, BioSectionContext ctx);

    /** JSON schema for the structured output. */
    Map<String, Object> schema();
}
