package com.planted.dto;

/**
 * LLM-generated narrative for one local calendar day of plant history (from latest completed summary job).
 */
public record HistoryDailyDigestDto(String day, String digest) {
}
