package com.planted.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "plant_history_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_id", nullable = false)
    private Long plantId;

    @Column(name = "note_text", columnDefinition = "TEXT")
    private String noteText;

    @Column(name = "image_id")
    private Long imageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
