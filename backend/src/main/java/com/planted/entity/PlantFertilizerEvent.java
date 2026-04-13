package com.planted.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "plant_fertilizer_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantFertilizerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_id", nullable = false)
    private Long plantId;

    @Column(name = "fertilized_at", nullable = false)
    private OffsetDateTime fertilizedAt;

    @Column(name = "fertilizer_type")
    private String fertilizerType;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
