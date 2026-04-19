package com.planted.repository;

import com.planted.entity.PlantBioSection;
import com.planted.entity.PlantBioSectionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantBioSectionRepository extends JpaRepository<PlantBioSection, Long> {

    List<PlantBioSection> findByPlantId(Long plantId);

    Optional<PlantBioSection> findByPlantIdAndSectionKey(Long plantId, PlantBioSectionKey sectionKey);
}
