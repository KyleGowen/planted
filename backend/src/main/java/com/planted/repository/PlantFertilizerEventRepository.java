package com.planted.repository;

import com.planted.entity.PlantFertilizerEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantFertilizerEventRepository extends JpaRepository<PlantFertilizerEvent, Long> {

    List<PlantFertilizerEvent> findByPlantIdOrderByFertilizedAtDesc(Long plantId);

    Optional<PlantFertilizerEvent> findFirstByPlantIdOrderByFertilizedAtDesc(Long plantId);

    boolean existsByPlantId(Long plantId);

    /** Recent fertilizer events across all plants, for the activity feed. */
    @Query("SELECT e FROM PlantFertilizerEvent e ORDER BY e.fertilizedAt DESC")
    List<PlantFertilizerEvent> findRecentAcrossAllPlants(Pageable pageable);
}
