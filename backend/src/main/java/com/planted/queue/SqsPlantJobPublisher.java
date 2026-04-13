package com.planted.queue;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("prod")
@RequiredArgsConstructor
public class SqsPlantJobPublisher implements PlantJobPublisher {

    private final AmazonSQS amazonSQS;
    private final ObjectMapper objectMapper;

    @Value("${planted.queue.sqs-queue-url}")
    private String queueUrl;

    @Override
    public void publish(PlantJobMessage message) {
        try {
            String body = objectMapper.writeValueAsString(message);
            amazonSQS.sendMessage(queueUrl, body);
            log.info("[SQS] Published job: {} for plantId={}", message.getJobType(), message.getPlantId());
        } catch (Exception e) {
            log.error("[SQS] Failed to publish job: {}", message, e);
            throw new RuntimeException("Failed to publish job to SQS", e);
        }
    }
}
