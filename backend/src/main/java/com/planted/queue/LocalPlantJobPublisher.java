package com.planted.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("local")
@RequiredArgsConstructor
public class LocalPlantJobPublisher implements PlantJobPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(PlantJobMessage message) {
        log.info("[LOCAL QUEUE] Publishing job: {} for plantId={}", message.getJobType(), message.getPlantId());
        eventPublisher.publishEvent(new PlantJobEvent(this, message));
    }
}
