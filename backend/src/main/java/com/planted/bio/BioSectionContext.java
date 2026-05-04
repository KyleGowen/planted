package com.planted.bio;

/**
 * Extra context gathered once per processor run and passed to every strategy.
 * Kept small and plant-scoped; strategies pull what they need.
 *
 * <p>{@code notesText} carries the owner's plain journal notes (newest first)
 * so bio strategies can honor OWNER CERTAINTY assertions written in free
 * text — e.g. "correcting the earlier identification; this is actually
 * Sansevieria cylindrica". The species-ID strategy uses this to override
 * photo-only inference when the owner asserts an ID; care strategies use it
 * to honor asserted working routines.
 */
public record BioSectionContext(
        String speciesName,
        String taxonomicFamily,
        String nativeRegions,
        String geographicLocation,
        String timelineText,
        String goalsText,
        String notesText) {

    public static BioSectionContext empty() {
        return new BioSectionContext(null, null, null, null, null, null, null);
    }
}
