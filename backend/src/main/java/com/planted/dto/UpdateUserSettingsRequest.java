package com.planted.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/user/settings.
 * <p>
 * All fields are nullable — null means "leave unchanged", except for
 * {@code openAiApiKeyOverride} where an explicit empty string clears the
 * stored key and null means "do not touch the key field".
 * </p>
 */
public record UpdateUserSettingsRequest(

        @Size(max = 100, message = "Display name must be at most 100 characters")
        String displayName,

        /**
         * Supply a new API key value to store, an empty string {@code ""} to
         * clear any stored override, or {@code null} to leave the current value
         * unchanged.
         */
        String openAiApiKeyOverride,

        @Min(value = 5, message = "Slide duration must be at least 5 seconds")
        @Max(value = 300, message = "Slide duration must be at most 300 seconds")
        Integer screensaverSlideDurationSeconds
) {
}
