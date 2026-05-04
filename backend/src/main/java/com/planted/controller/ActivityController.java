package com.planted.controller;

import com.planted.dto.ActivityEntryDto;
import com.planted.service.PlantQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final PlantQueryService queryService;

    /**
     * Returns a chronological feed of recent care events (watering, fertilizing, pruning,
     * journal entries) across all active plants. Used by the mobile activity tab.
     *
     * @param limit maximum entries to return (default 100, max 200)
     */
    @GetMapping
    public ResponseEntity<List<ActivityEntryDto>> listActivity(
            @RequestParam(defaultValue = "100") int limit) {
        int capped = Math.min(limit, 200);
        return ResponseEntity.ok(queryService.listRecentActivity(capped));
    }
}
