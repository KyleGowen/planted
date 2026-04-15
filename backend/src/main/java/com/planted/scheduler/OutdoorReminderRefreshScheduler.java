package com.planted.scheduler;

import com.planted.queue.PlantJobMessage;
import com.planted.queue.PlantJobPublisher;
import com.planted.repository.PlantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically enqueues reminder recompute for outdoor plants with coordinates so
 * weather-informed instructions stay fresh without blocking HTTP reads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "planted.outdoor-reminder-refresh.enabled", havingValue = "true", matchIfMissing = true)
public class OutdoorReminderRefreshScheduler {

    private final PlantRepository plantRepository;
    private final PlantJobPublisher jobPublisher;

    @Scheduled(cron = "${planted.outdoor-reminder-refresh.cron:0 0 */8 * * *}")
    public void enqueueOutdoorReminderRefresh() {
        List<Long> ids = plantRepository.findActiveOutdoorPlantIdsWithCoordinates();
        if (ids.isEmpty()) {
            return;
        }
        log.info("Outdoor reminder refresh: enqueueing {} plants", ids.size());
        for (Long id : ids) {
            jobPublisher.publish(PlantJobMessage.builder()
                    .jobType(PlantJobMessage.JobType.PLANT_REMINDER_RECOMPUTE)
                    .plantId(id)
                    .build());
        }
    }
}
