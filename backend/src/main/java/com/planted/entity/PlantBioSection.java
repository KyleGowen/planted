package com.planted.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Cached output of a single {@link PlantBioSectionKey} for a plant. Rows are
 * upserted by {@code PlantBioSectionProcessor} and read directly by the API;
 * staleness and invalidation are expressed through {@link #generatedAt} and
 * {@link #inputsFingerprint}.
 */
@Entity
@Table(name = "plant_bio_sections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantBioSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_id", nullable = false)
    private Long plantId;

    @Column(name = "section_key", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlantBioSectionKey sectionKey;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    @Type(JsonBinaryType.class)
    @Column(name = "content_jsonb", columnDefinition = "jsonb")
    private Map<String, Object> contentJsonb;

    @Column(name = "prompt_key")
    private String promptKey;

    @Column(name = "prompt_version")
    private Integer promptVersion;

    @Column(name = "inputs_fingerprint", length = 64)
    private String inputsFingerprint;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
