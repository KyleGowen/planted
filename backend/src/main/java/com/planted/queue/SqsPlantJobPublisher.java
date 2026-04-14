package com.planted.queue;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@Profile("prod")
@RequiredArgsConstructor
public class SqsPlantJobPublisher implements PlantJobPublisher {

    private final AmazonSQS amazonSQS;
    private final ObjectMapper objectMapper;

    @Value("${planted.queue.sqs-queue-url}")
    private String queueUrl;

    /**
     * Sends after DB commit (same idea as {@link LocalPlantJobPublisher}). Sending inside the
     * transaction used to roll back the {@code plant_analyses} insert and surface as HTTP 500 when SQS
     * was misconfigured or unreachable.
     */
    @Override
    public void publish(PlantJobMessage message) {
        Runnable send = () -> {
            try {
                String body = objectMapper.writeValueAsString(message);
                amazonSQS.sendMessage(queueUrl, body);
                log.info("[SQS] Published job: {} for plantId={}", message.getJobType(), message.getPlantId());
            } catch (Exception e) {
                log.error(
                        "[SQS] Failed to publish job type={} plantId={} analysisId={} — "
                                + "row may be stuck PENDING; fix queue/credentials and retry or reconcile.",
                        message.getJobType(),
                        message.getPlantId(),
                        message.getAnalysisId(),
                        e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
