package com.planted.repository;

import com.planted.entity.PlantPruneEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantPruneEventRepository extends JpaRepository<PlantPruneEvent, Long> {

    List<PlantPruneEvent> findByPlantIdOrderByPrunedAtDesc(Long plantId);

    Optional<PlantPruneEvent> findFirstByPlantIdOrderByPrunedAtDesc(Long plantId);
}
