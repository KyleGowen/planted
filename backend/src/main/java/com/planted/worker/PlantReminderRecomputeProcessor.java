package com.planted.worker;

import com.planted.queue.PlantJobMessage;
import com.planted.service.PlantReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlantReminderRecomputeProcessor {

    private final PlantReminderService reminderService;

    public void process(PlantJobMessage message) {
        Long plantId = message.getPlantId();
        log.info("Recomputing reminder state for plant {}", plantId);
        reminderService.recomputeReminderState(plantId);
    }
}
