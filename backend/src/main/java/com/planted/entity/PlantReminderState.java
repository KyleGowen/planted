package com.planted.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "plant_reminder_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantReminderState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_id", nullable = false, unique = true)
    private Long plantId;

    @Column(name = "watering_due", nullable = false)
    private boolean wateringDue = false;

    @Column(name = "watering_overdue", nullable = false)
    private boolean wateringOverdue = false;

    @Column(name = "fertilizer_due", nullable = false)
    private boolean fertilizerDue = false;

    @Column(name = "pruning_due", nullable = false)
    private boolean pruningDue = false;

    @Column(name = "health_attention_needed", nullable = false)
    private boolean healthAttentionNeeded = false;

    @Column(name = "light_attention_needed", nullable = false)
    private boolean lightAttentionNeeded = false;

    @Column(name = "placement_attention_needed", nullable = false)
    private boolean placementAttentionNeeded = false;

    /** Unused — see goal_attention_needed column kept for backward compat only; nothing reads/writes this anymore. */
    @Column(name = "goal_attention_needed", nullable = false)
    private boolean goalAttentionNeeded = false;

    @Column(name = "next_watering_instruction", columnDefinition = "TEXT")
    private String nextWateringInstruction;

    @Column(name = "next_fertilizer_instruction", columnDefinition = "TEXT")
    private String nextFertilizerInstruction;

    @Column(name = "next_pruning_instruction", columnDefinition = "TEXT")
    private String nextPruningInstruction;

    /** Short LLM-authored reason surfaced as tooltip/aria on the health icon when {@link #healthAttentionNeeded} is true. */
    @Column(name = "health_attention_reason", columnDefinition = "TEXT")
    private String healthAttentionReason;

    /** Short LLM-authored reason surfaced as tooltip/aria on the light icon when {@link #lightAttentionNeeded} is true. */
    @Column(name = "light_attention_reason", columnDefinition = "TEXT")
    private String lightAttentionReason;

    /** Short LLM-authored reason surfaced as tooltip/aria on the placement icon when {@link #placementAttentionNeeded} is true. */
    @Column(name = "placement_attention_reason", columnDefinition = "TEXT")
    private String placementAttentionReason;

    /** Short outdoor conditions summary from weather API (rain, heat); null for indoor or when unavailable. */
    @Column(name = "weather_care_note", columnDefinition = "TEXT")
    private String weatherCareNote;

    @Column(name = "last_computed_at", nullable = false)
    private OffsetDateTime lastComputedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        lastComputedAt = OffsetDateTime.now();
    }
}
