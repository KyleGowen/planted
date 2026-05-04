package com.planted.dto;

/**
 * Settings returned to the client. The OpenAI API key override is NEVER included —
 * only a boolean indicating whether an override is currently configured.
 */
public record UserSettingsResponse(
        String displayName,
        boolean apiKeyConfigured,
        int screensaverSlideDurationSeconds
) {
}
