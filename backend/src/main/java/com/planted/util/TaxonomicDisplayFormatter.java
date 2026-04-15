package com.planted.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the canonical species heading: family, genus, specific epithet, and variety,
 * separated by single spaces, omitting blank parts.
 */
public final class TaxonomicDisplayFormatter {

    private TaxonomicDisplayFormatter() {
    }

    /**
     * @return joined display line, or null if every part is blank
     */
    public static String formatLine(String taxonomicFamily, String genus, String species, String variety) {
        List<String> parts = new ArrayList<>(4);
        addIfPresent(parts, taxonomicFamily);
        addIfPresent(parts, genus);
        addIfPresent(parts, species);
        addIfPresent(parts, variety);
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" ", parts);
    }

    /**
     * Binomial or best-effort fallback when order is unknown (e.g. legacy rows).
     */
    public static String formatBinomial(String genus, String species) {
        if (genus == null || genus.isBlank()) {
            return species != null && !species.isBlank() ? species.trim() : null;
        }
        if (species == null || species.isBlank()) {
            return genus.trim();
        }
        return genus.trim() + " " + species.trim();
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value == null) {
            return;
        }
        String t = value.trim();
        if (!t.isEmpty()) {
            parts.add(t);
        }
    }
}
