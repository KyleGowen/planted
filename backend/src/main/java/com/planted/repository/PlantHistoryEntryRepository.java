package com.planted.repository;

import com.planted.entity.PlantHistoryEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlantHistoryEntryRepository extends JpaRepository<PlantHistoryEntry, Long> {

    List<PlantHistoryEntry> findByPlantIdOrderByCreatedAtDesc(Long plantId);

    List<PlantHistoryEntry> findByPlantIdOrderByCreatedAtAsc(Long plantId);

    List<PlantHistoryEntry> findTop5ByPlantIdAndNoteTextIsNotNullOrderByCreatedAtDesc(Long plantId);

    long countByPlantId(Long plantId);

    /** Recent journal entries across all plants, for the activity feed. */
    @Query("SELECT e FROM PlantHistoryEntry e ORDER BY e.createdAt DESC")
    List<PlantHistoryEntry> findRecentAcrossAllPlants(Pageable pageable);
}
