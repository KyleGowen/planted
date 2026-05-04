package com.planted.service;

import com.planted.dto.UpdateUserSettingsRequest;
import com.planted.dto.UserSettingsResponse;
import com.planted.entity.UserSettings;
import com.planted.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository repository;

    @Value("${planted.user.default-id:default}")
    private String defaultUserId;

    /** Env-var / application.yml fallback key; injected at startup. */
    @Value("${planted.openai.api-key:}")
    private String envApiKey;

    // ── Read ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettingsForDefaultUser() {
        return repository.findById(defaultUserId)
                .map(this::toResponse)
                .orElseGet(this::defaultResponse);
    }

    /**
     * Returns the effective OpenAI API key: the DB override takes precedence
     * over the environment variable so the key can be updated without a restart.
     * The value is NEVER logged.
     */
    @Transactional(readOnly = true)
    public String getEffectiveOpenAiApiKey() {
        return repository.findById(defaultUserId)
                .map(UserSettings::getOpenaiApiKeyOverride)
                .filter(k -> k != null && !k.isBlank())
                .map(k -> {
                    log.debug("OpenAI API key source: database override");
                    return k;
                })
                .orElseGet(() -> {
                    log.debug("OpenAI API key source: environment / application.yml");
                    return envApiKey;
                });
    }

    // ── Write ───────────────────────────────────────────────────────────────

    @Transactional
    public UserSettingsResponse updateSettingsForDefaultUser(UpdateUserSettingsRequest req) {
        UserSettings settings = repository.findById(defaultUserId)
                .orElseGet(() -> UserSettings.builder().userId(defaultUserId).build());

        // null = leave unchanged; "" = clear; non-blank = set.
        // Each settings field is saved independently from the frontend, so omitted JSON fields
        // arrive as null — we must treat null as "no change" for all fields.
        if (req.displayName() != null) {
            String trimmed = req.displayName().trim();
            settings.setDisplayName(trimmed.isEmpty() ? null : trimmed);
        }

        if (req.openAiApiKeyOverride() != null) {
            String trimmed = req.openAiApiKeyOverride().trim();
            settings.setOpenaiApiKeyOverride(trimmed.isEmpty() ? null : trimmed);
            log.info("OpenAI API key override {}", trimmed.isEmpty() ? "cleared" : "updated");
        }

        if (req.screensaverSlideDurationSeconds() != null) {
            settings.setScreensaverSlideDurationSeconds(req.screensaverSlideDurationSeconds());
        }

        return toResponse(repository.save(settings));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UserSettingsResponse toResponse(UserSettings s) {
        boolean keyConfigured = (s.getOpenaiApiKeyOverride() != null && !s.getOpenaiApiKeyOverride().isBlank())
                || (envApiKey != null && !envApiKey.isBlank());
        return new UserSettingsResponse(
                s.getDisplayName(),
                keyConfigured,
                s.getScreensaverSlideDurationSeconds()
        );
    }

    private UserSettingsResponse defaultResponse() {
        boolean keyConfigured = envApiKey != null && !envApiKey.isBlank();
        return new UserSettingsResponse(null, keyConfigured, 60);
    }
}
