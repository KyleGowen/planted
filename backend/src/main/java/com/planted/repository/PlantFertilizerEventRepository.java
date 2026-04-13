package com.planted.repository;

import com.planted.entity.PlantFertilizerEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantFertilizerEventRepository extends JpaRepository<PlantFertilizerEvent, Long> {

    List<PlantFertilizerEvent> findByPlantIdOrderByFertilizedAtDesc(Long plantId);

    Optional<PlantFertilizerEvent> findFirstByPlantIdOrderByFertilizedAtDesc(Long plantId);
}
