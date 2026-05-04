package com.planted.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "display_name", length = 100)
    private String displayName;

    /** Stored as plaintext; never returned via API. Overrides the OPENAI_API_KEY env var when set. */
    @Column(name = "openai_api_key_override", columnDefinition = "TEXT")
    private String openaiApiKeyOverride;

    @Column(name = "screensaver_slide_duration_seconds", nullable = false)
    @Builder.Default
    private int screensaverSlideDurationSeconds = 60;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = OffsetDateTime.now();
    }
}
