package com.planted.repository;

import com.planted.entity.Plant;
import com.planted.entity.PlantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantRepository extends JpaRepository<Plant, Long> {

    List<Plant> findByStatusOrderByCreatedAtDesc(PlantStatus status);

    Optional<Plant> findByIdAndStatus(Long id, PlantStatus status);

    @Query("SELECT p FROM Plant p WHERE p.status = 'ACTIVE' ORDER BY p.createdAt DESC")
    List<Plant> findAllActive();

    @Query("SELECT p.id FROM Plant p WHERE p.status = 'ACTIVE' AND p.growingContext = 'OUTDOOR' "
            + "AND p.latitude IS NOT NULL AND p.longitude IS NOT NULL")
    List<Long> findActiveOutdoorPlantIdsWithCoordinates();
}
