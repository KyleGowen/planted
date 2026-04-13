package com.planted.repository;

import com.planted.entity.LlmRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LlmRequestRepository extends JpaRepository<LlmRequest, Long> {

    List<LlmRequest> findByPlantIdOrderByCreatedAtDesc(Long plantId);

    List<LlmRequest> findByAnalysisIdOrderByCreatedAtDesc(Long analysisId);
}
