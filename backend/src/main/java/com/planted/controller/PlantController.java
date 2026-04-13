package com.planted.controller;

import com.planted.dto.*;
import com.planted.service.PlantCommandService;
import com.planted.service.PlantQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/plants")
@RequiredArgsConstructor
public class PlantController {

    private final PlantCommandService commandService;
    private final PlantQueryService queryService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CreatePlantResponse> registerPlant(
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "name", required = false) String name,
            @RequestPart(value = "location", required = false) String location,
            @RequestPart(value = "goalsText", required = false) String goalsText,
            @RequestPart(value = "lastWateredAt", required = false) String lastWateredAt) {

        OffsetDateTime lastWatered = lastWateredAt != null ? OffsetDateTime.parse(lastWateredAt) : null;
        CreatePlantResponse response = commandService.registerPlant(image, name, location, goalsText, lastWatered);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PlantListItemResponse>> listPlants() {
        return ResponseEntity.ok(queryService.listActivePlants());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlantDetailResponse> getPlant(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getPlantDetail(id));
    }

    @PostMapping("/{id}/waterings")
    public ResponseEntity<Void> recordWatering(
            @PathVariable Long id,
            @Valid @RequestBody RecordWateringRequest request) {
        commandService.recordWatering(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/fertilizers")
    public ResponseEntity<Void> recordFertilizer(
            @PathVariable Long id,
            @Valid @RequestBody RecordFertilizerRequest request) {
        commandService.recordFertilizer(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping(value = "/{id}/prunes", consumes = "multipart/form-data")
    public ResponseEntity<Void> recordPrune(
            @PathVariable Long id,
            @RequestPart("prunedAt") String prunedAt,
            @RequestPart(value = "notes", required = false) String notes,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        RecordPruneRequest request = new RecordPruneRequest(OffsetDateTime.parse(prunedAt), notes);
        commandService.recordPrune(id, request, image);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping(value = "/{id}/pruning-analysis", consumes = "multipart/form-data")
    public ResponseEntity<RequestPruningAnalysisResponse> requestPruningAnalysis(
            @PathVariable Long id,
            @RequestPart("images") List<MultipartFile> images) {
        RequestPruningAnalysisResponse response = commandService.requestPruningAnalysis(id, images);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archivePlant(@PathVariable Long id) {
        commandService.archivePlant(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restorePlant(@PathVariable Long id) {
        commandService.restorePlant(id);
        return ResponseEntity.ok().build();
    }
}
