package com.planted.repository;

import com.planted.entity.PlantPruneEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantPruneEventRepository extends JpaRepository<PlantPruneEvent, Long> {

    List<PlantPruneEvent> findByPlantIdOrderByPrunedAtDesc(Long plantId);

    Optional<PlantPruneEvent> findFirstByPlantIdOrderByPrunedAtDesc(Long plantId);

    boolean existsByPlantId(Long plantId);

    /** Recent prune events across all plants, for the activity feed. */
    @Query("SELECT e FROM PlantPruneEvent e ORDER BY e.prunedAt DESC")
    List<PlantPruneEvent> findRecentAcrossAllPlants(Pageable pageable);
}
