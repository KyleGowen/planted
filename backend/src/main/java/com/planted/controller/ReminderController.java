package com.planted.controller;

import com.planted.dto.PlantReminderResponse;
import com.planted.service.PlantReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final PlantReminderService reminderService;

    @GetMapping("/watering")
    public ResponseEntity<List<PlantReminderResponse>> getWateringReminders() {
        return ResponseEntity.ok(reminderService.getWateringReminders());
    }

    @GetMapping("/fertilizing")
    public ResponseEntity<List<PlantReminderResponse>> getFertilizerReminders() {
        return ResponseEntity.ok(reminderService.getFertilizerReminders());
    }

    @GetMapping("/pruning")
    public ResponseEntity<List<PlantReminderResponse>> getPruningReminders() {
        return ResponseEntity.ok(reminderService.getPruningReminders());
    }
}
