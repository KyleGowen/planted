package com.planted.repository;

import com.planted.entity.PlantAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlantAnalysisRepository extends JpaRepository<PlantAnalysis, Long> {

    List<PlantAnalysis> findByPlantIdOrderByCreatedAtDesc(Long plantId);

    Optional<PlantAnalysis> findFirstByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(
            Long plantId, PlantAnalysis.AnalysisType analysisType);

    List<PlantAnalysis> findByPlantIdAndStatusIn(Long plantId, List<PlantAnalysis.AnalysisStatus> statuses);

    List<PlantAnalysis> findByStatus(PlantAnalysis.AnalysisStatus status);
}
