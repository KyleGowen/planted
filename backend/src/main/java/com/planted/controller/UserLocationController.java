package com.planted.controller;

import com.planted.dto.UpdateUserLocationRequest;
import com.planted.dto.UserLocationResponse;
import com.planted.service.UserPhysicalAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/location")
@RequiredArgsConstructor
public class UserLocationController {

    private final UserPhysicalAddressService userPhysicalAddressService;

    @GetMapping
    public ResponseEntity<UserLocationResponse> getLocation() {
        return ResponseEntity.ok(new UserLocationResponse(
                userPhysicalAddressService.getAddressForDefaultUser().orElse(null)));
    }

    @PutMapping
    public ResponseEntity<UserLocationResponse> putLocation(
            @Valid @RequestBody UpdateUserLocationRequest request) {
        userPhysicalAddressService.upsertForDefaultUser(request.address());
        return ResponseEntity.ok(new UserLocationResponse(
                userPhysicalAddressService.getAddressForDefaultUser().orElse(null)));
    }
}
