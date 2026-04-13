package com.planted.repository;

import com.planted.entity.PlantImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantImageRepository extends JpaRepository<PlantImage, Long> {

    List<PlantImage> findByPlantIdOrderBySortOrderAscCreatedAtAsc(Long plantId);

    List<PlantImage> findByPlantIdAndImageTypeOrderBySortOrderAscCreatedAtAsc(
            Long plantId, PlantImage.ImageType imageType);

    Optional<PlantImage> findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(
            Long plantId, PlantImage.ImageType imageType);
}
