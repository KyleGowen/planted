package com.planted.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaxonomicDisplayFormatterTest {

    @Test
    void formatLine_joinsFamilyGenusEpithetVariety() {
        assertEquals(
                "Asparagaceae Dracaena trifasciata 'Laurentii'",
                TaxonomicDisplayFormatter.formatLine(
                        "Asparagaceae", "Dracaena", "trifasciata", "'Laurentii'"));
    }

    @Test
    void formatLine_skipsBlanks() {
        assertEquals(
                "Dracaena trifasciata",
                TaxonomicDisplayFormatter.formatLine("", "Dracaena", "trifasciata", null));
    }

    @Test
    void formatLine_nullWhenAllBlank() {
        assertNull(TaxonomicDisplayFormatter.formatLine(null, " ", "", null));
    }

    @Test
    void formatBinomial() {
        assertEquals("Dracaena trifasciata",
                TaxonomicDisplayFormatter.formatBinomial("Dracaena", "trifasciata"));
        assertEquals("Dracaena", TaxonomicDisplayFormatter.formatBinomial("Dracaena", null));
        assertNull(TaxonomicDisplayFormatter.formatBinomial(null, null));
    }
}
