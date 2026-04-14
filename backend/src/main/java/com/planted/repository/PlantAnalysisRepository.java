package com.planted.repository;

import com.planted.entity.PlantAnalysis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlantAnalysisRepository extends JpaRepository<PlantAnalysis, Long> {

    List<PlantAnalysis> findByPlantIdOrderByCreatedAtDesc(Long plantId);

    /**
     * Newest row for one analysis type. Prefer {@code PageRequest.of(0,1)} over {@code findFirst...}
     * {@link Optional} queries so multiple DB rows can never trigger a non-unique result.
     */
    List<PlantAnalysis> findByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(
            Long plantId, PlantAnalysis.AnalysisType analysisType, Pageable pageable);

    /**
     * Newest row among several analysis types (e.g. registration + reanalysis).
     */
    List<PlantAnalysis> findByPlantIdAndAnalysisTypeInOrderByCreatedAtDesc(
            Long plantId, List<PlantAnalysis.AnalysisType> analysisTypes, Pageable pageable);

    Optional<PlantAnalysis> findFirstByPlantIdAndAnalysisTypeOrderByCreatedAtDesc(
            Long plantId, PlantAnalysis.AnalysisType analysisType);

    Optional<PlantAnalysis> findFirstByPlantIdAndAnalysisTypeInOrderByCreatedAtDesc(
            Long plantId, List<PlantAnalysis.AnalysisType> analysisTypes);

    /**
     * Latest completed registration or reanalysis for care snapshot (history summary, etc.).
     */
    Optional<PlantAnalysis> findFirstByPlantIdAndAnalysisTypeInAndStatusOrderByCompletedAtDescIdDesc(
            Long plantId,
            Collection<PlantAnalysis.AnalysisType> analysisTypes,
            PlantAnalysis.AnalysisStatus status);

    List<PlantAnalysis> findByPlantIdAndStatusIn(Long plantId, List<PlantAnalysis.AnalysisStatus> statuses);

    List<PlantAnalysis> findByStatus(PlantAnalysis.AnalysisStatus status);

    Optional<PlantAnalysis> findFirstByPlantIdAndAnalysisTypeAndStatusOrderByCompletedAtDesc(
            Long plantId,
            PlantAnalysis.AnalysisType analysisType,
            PlantAnalysis.AnalysisStatus status);

    /**
     * Latest completed history summary text for the About page. Multiple INFO_PANEL rows may exist
     * (re-runs); callers must limit to one result — {@link Optional} single-result queries throw
     * if the DB returns more than one row.
     */
    @Query("""
            select a from PlantAnalysis a
            where a.plantId = :plantId
              and a.analysisType = :analysisType
              and a.status = :status
              and a.infoPanelSummary is not null
              and trim(a.infoPanelSummary) <> ''
            order by a.completedAt desc nulls last, a.id desc
            """)
    List<PlantAnalysis> findLatestCompletedHistorySummaryWithBody(
            @Param("plantId") Long plantId,
            @Param("analysisType") PlantAnalysis.AnalysisType analysisType,
            @Param("status") PlantAnalysis.AnalysisStatus status,
            Pageable pageable);
}
