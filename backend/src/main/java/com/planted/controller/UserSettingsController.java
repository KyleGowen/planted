package com.planted.controller;

import com.planted.dto.UpdateUserSettingsRequest;
import com.planted.dto.UserSettingsResponse;
import com.planted.service.UserSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/settings")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @GetMapping
    public ResponseEntity<UserSettingsResponse> getSettings() {
        return ResponseEntity.ok(userSettingsService.getSettingsForDefaultUser());
    }

    @PutMapping
    public ResponseEntity<UserSettingsResponse> putSettings(
            @Valid @RequestBody UpdateUserSettingsRequest request) {
        return ResponseEntity.ok(userSettingsService.updateSettingsForDefaultUser(request));
    }
}
