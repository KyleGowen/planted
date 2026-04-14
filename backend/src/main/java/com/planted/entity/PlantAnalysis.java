package com.planted.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "plant_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_id", nullable = false)
    private Long plantId;

    @Column(name = "analysis_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AnalysisType analysisType;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AnalysisStatus status = AnalysisStatus.PENDING;

    // Taxonomy
    @Column(name = "class_name")
    private String className;

    @Column(name = "genus")
    private String genus;

    @Column(name = "species")
    private String species;

    @Column(name = "variety")
    private String variety;

    @Column(name = "scientific_name")
    private String scientificName;

    @Column(name = "confidence")
    private String confidence;

    // Care — normalized fields
    @Type(JsonBinaryType.class)
    @Column(name = "native_regions_json", columnDefinition = "jsonb")
    private List<String> nativeRegionsJson;

    @Column(name = "placement_guidance", columnDefinition = "TEXT")
    private String placementGuidance;

    @Column(name = "light_needs")
    private String lightNeeds;

    @Column(name = "watering_guidance", columnDefinition = "TEXT")
    private String wateringGuidance;

    @Column(name = "watering_amount")
    private String wateringAmount;

    @Column(name = "watering_frequency")
    private String wateringFrequency;

    @Column(name = "fertilizer_guidance", columnDefinition = "TEXT")
    private String fertilizerGuidance;

    @Column(name = "fertilizer_type")
    private String fertilizerType;

    @Column(name = "fertilizer_frequency")
    private String fertilizerFrequency;

    @Column(name = "pruning_guidance", columnDefinition = "TEXT")
    private String pruningGuidance;

    @Column(name = "propagation_instructions", columnDefinition = "TEXT")
    private String propagationInstructions;

    @Column(name = "health_diagnosis", columnDefinition = "TEXT")
    private String healthDiagnosis;

    @Column(name = "goal_suggestions", columnDefinition = "TEXT")
    private String goalSuggestions;

    @Type(JsonBinaryType.class)
    @Column(name = "interesting_facts_json", columnDefinition = "jsonb")
    private List<String> interestingFactsJson;

    @Column(name = "species_overview", columnDefinition = "TEXT")
    private String speciesOverview;

    @Type(JsonBinaryType.class)
    @Column(name = "uses_json", columnDefinition = "jsonb")
    private List<String> usesJson;

    // Raw LLM output preserved for future prompt iteration
    @Type(JsonBinaryType.class)
    @Column(name = "raw_model_response_jsonb", columnDefinition = "jsonb")
    private Map<String, Object> rawModelResponseJsonb;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /** Narrative summary for {@link AnalysisType#INFO_PANEL} jobs (plant history timeline). */
    @Column(name = "info_panel_summary", columnDefinition = "TEXT")
    private String infoPanelSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum AnalysisType {
        REGISTRATION,
        REANALYSIS,
        PRUNING,
        REMINDER,
        INFO_PANEL
    }

    public enum AnalysisStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
