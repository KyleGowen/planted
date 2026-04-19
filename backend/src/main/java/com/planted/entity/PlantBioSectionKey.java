package com.planted.entity;

import java.time.Duration;
import java.util.List;

/**
 * Decomposed bio sections cached per-plant in {@code plant_bio_sections}.
 *
 * <p>Each section is produced by its own dedicated small LLM call (two vision,
 * seven text) so any single field can be refreshed independently of the
 * others. Sections are served out of the cache table; refreshes are enqueued
 * lazily when a section is stale or when an invalidation signal fires.
 */
public enum PlantBioSectionKey {
    SPECIES_ID(true, Duration.ofDays(3650)),
    HEALTH_ASSESSMENT(true, Duration.ofDays(3650)),
    SPECIES_DESCRIPTION(false, Duration.ofDays(3650)),
    WATER_CARE(false, Duration.ofHours(24)),
    FERTILIZER_CARE(false, Duration.ofHours(24)),
    PRUNING_CARE(false, Duration.ofHours(24)),
    LIGHT_CARE(false, Duration.ofHours(24)),
    PLACEMENT_CARE(false, Duration.ofHours(24)),
    HISTORY_SUMMARY(false, Duration.ofHours(24));

    private final boolean requiresImage;
    private final Duration ttl;

    PlantBioSectionKey(boolean requiresImage, Duration ttl) {
        this.requiresImage = requiresImage;
        this.ttl = ttl;
    }

    public boolean requiresImage() {
        return requiresImage;
    }

    public Duration ttl() {
        return ttl;
    }

    /** Sections that depend on the resolved species — refreshed whenever SPECIES_ID changes. */
    public static List<PlantBioSectionKey> speciesDependent() {
        return List.of(
                SPECIES_DESCRIPTION,
                WATER_CARE,
                FERTILIZER_CARE,
                PRUNING_CARE,
                LIGHT_CARE,
                PLACEMENT_CARE,
                HISTORY_SUMMARY,
                HEALTH_ASSESSMENT);
    }
}
