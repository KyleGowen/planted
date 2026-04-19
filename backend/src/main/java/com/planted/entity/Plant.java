package com.planted.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.OffsetDateTime;

@Entity
@Table(name = "plants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "name")
    private String name;

    @Column(name = "location")
    private String location;

    /** One-sentence LLM paraphrase of {@link #location} shown under Indoor/Outdoor in the UI. */
    @Column(name = "placement_notes_summary", columnDefinition = "TEXT")
    private String placementNotesSummary;

    @Column(name = "geo_country")
    private String geoCountry;

    @Column(name = "geo_state")
    private String geoState;

    @Column(name = "geo_city")
    private String geoCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "growing_context", nullable = false)
    @Builder.Default
    private PlantGrowingContext growingContext = PlantGrowingContext.INDOOR;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "goals_text", columnDefinition = "TEXT")
    private String goalsText;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlantStatus status = PlantStatus.ACTIVE;

    @Column(name = "primary_image_id")
    private Long primaryImageId;

    @Column(name = "illustrated_image_asset_id")
    private Long illustratedImageAssetId;

    @Column(name = "species_label")
    private String speciesLabel;

    @Column(name = "genus")
    private String genus;

    @Column(name = "species")
    private String species;

    @Column(name = "variety")
    private String variety;

    @Column(name = "class_name")
    private String className;

    /** Botanical family when known; mirrors latest analysis for quick reads. */
    @Column(name = "taxonomic_family")
    private String taxonomicFamily;

    @Column(name = "health_attention_needed", nullable = false)
    private boolean healthAttentionNeeded = false;

    @Column(name = "goal_attention_needed", nullable = false)
    private boolean goalAttentionNeeded = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
