package com.planted.service;

import com.planted.entity.PlantHistoryEntry;
import com.planted.repository.PlantHistoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a plain owner-notes text blob for bio-section prompts. This is the
 * single source for owner free-text journal entries that feed the OWNER
 * CERTAINTY rule in bio prompts (species_id / species_description / care
 * sections / health assessment). Intentionally excludes care events — those
 * live in {@link CareHistoryFormatter}; bio prompts only care about owner
 * assertions, not "watered on 2026-04-19".
 *
 * <p>Format: newest entries first, one per line, prefixed with an ISO date.
 * Blank / null entries are skipped. Capped at {@link #MAX_ENTRIES} so a plant
 * with hundreds of notes cannot explode the prompt.
 */
@Component
@RequiredArgsConstructor
public class OwnerNoteFormatter {

    public static final int MAX_ENTRIES = 25;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final PlantHistoryEntryRepository historyEntryRepository;

    /**
     * Returns the owner's journal notes as LLM-friendly text, newest first.
     * Returns {@code null} when the plant has no textual notes so callers can
     * default to empty without materializing "no notes" strings.
     */
    public String formatForLlm(Long plantId) {
        List<PlantHistoryEntry> entries = historyEntryRepository
                .findByPlantIdOrderByCreatedAtDesc(plantId);
        if (entries.isEmpty()) return null;
        List<String> lines = new ArrayList<>();
        int used = 0;
        for (PlantHistoryEntry entry : entries) {
            if (used >= MAX_ENTRIES) break;
            String text = entry.getNoteText();
            if (text == null) continue;
            String trimmed = text.trim();
            if (trimmed.isEmpty()) continue;
            String datePrefix = entry.getCreatedAt() != null
                    ? entry.getCreatedAt().toLocalDate().format(DATE_FMT) + ": "
                    : "";
            lines.add(datePrefix + trimmed);
            used++;
        }
        if (lines.isEmpty()) return null;
        return String.join("\n", lines);
    }
}
