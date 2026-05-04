package com.planted.repository;

import com.planted.entity.PlantWateringEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantWateringEventRepository extends JpaRepository<PlantWateringEvent, Long> {

    List<PlantWateringEvent> findByPlantIdOrderByWateredAtDesc(Long plantId);

    List<PlantWateringEvent> findTop20ByPlantIdOrderByWateredAtDesc(Long plantId);

    Optional<PlantWateringEvent> findFirstByPlantIdOrderByWateredAtDesc(Long plantId);

    boolean existsByPlantId(Long plantId);

    /** Recent watering events across all plants, for the activity feed. */
    @Query("SELECT e FROM PlantWateringEvent e ORDER BY e.wateredAt DESC")
    List<PlantWateringEvent> findRecentAcrossAllPlants(Pageable pageable);
}
