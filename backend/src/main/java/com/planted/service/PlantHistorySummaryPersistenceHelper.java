package com.planted.service;

import com.planted.entity.PlantAnalysis;
import com.planted.repository.PlantAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Short transactions for history-summary job state so the DB connection is not held during OpenAI calls.
 */
@Service
@RequiredArgsConstructor
public class PlantHistorySummaryPersistenceHelper {

    private static final int MAX_FAILURE_REASON = 2000;

    private final PlantAnalysisRepository analysisRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long analysisId) {
        PlantAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
        analysis.setStatus(PlantAnalysis.AnalysisStatus.PROCESSING);
        analysisRepository.save(analysis);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long analysisId, String summary, Map<String, Object> raw) {
        PlantAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
        analysis.setInfoPanelSummary(summary);
        analysis.setRawModelResponseJsonb(raw);
        analysis.setStatus(PlantAnalysis.AnalysisStatus.COMPLETED);
        analysis.setCompletedAt(OffsetDateTime.now());
        analysis.setFailureReason(null);
        analysisRepository.save(analysis);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long analysisId, String reason) {
        PlantAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
        analysis.setStatus(PlantAnalysis.AnalysisStatus.FAILED);
        String trimmed = reason == null ? "Unknown error" : reason;
        if (trimmed.length() > MAX_FAILURE_REASON) {
            trimmed = trimmed.substring(0, MAX_FAILURE_REASON) + "…";
        }
        analysis.setFailureReason(trimmed);
        analysisRepository.save(analysis);
    }
}
