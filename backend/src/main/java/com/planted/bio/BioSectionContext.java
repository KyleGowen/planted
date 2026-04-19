package com.planted.bio;

/**
 * Extra context gathered once per processor run and passed to every strategy.
 * Kept small and plant-scoped; strategies pull what they need.
 */
public record BioSectionContext(
        String speciesName,
        String taxonomicFamily,
        String nativeRegions,
        String geographicLocation,
        String timelineText,
        String goalsText) {

    public static BioSectionContext empty() {
        return new BioSectionContext(null, null, null, null, null, null);
    }
}
