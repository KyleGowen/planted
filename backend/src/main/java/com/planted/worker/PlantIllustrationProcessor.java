package com.planted.worker;

import com.planted.client.OpenAiImageGenerationClient;
import com.planted.entity.Plant;
import com.planted.entity.PlantImage;
import com.planted.queue.PlantJobMessage;
import com.planted.repository.PlantImageRepository;
import com.planted.repository.PlantRepository;
import com.planted.storage.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlantIllustrationProcessor {

    private final PlantRepository plantRepository;
    private final PlantImageRepository imageRepository;
    private final OpenAiImageGenerationClient imageGenClient;
    private final ImageStorageService imageStorageService;

    @Transactional
    public void process(PlantJobMessage message) {
        Long plantId = message.getPlantId();

        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));

        try {
            log.info("Generating illustrated image for plant {}", plantId);

            byte[] imageBytes = imageGenClient.generateIllustratedPlantImage(
                    plant.getGenus(), plant.getSpecies(), plant.getLocation());

            String filename = "illustrated_" + UUID.randomUUID() + ".png";
            String storagePath = imageStorageService.storeBytes(imageBytes, filename, "image/png", plantId);

            PlantImage illustratedImage = PlantImage.builder()
                    .plantId(plantId)
                    .imageType(PlantImage.ImageType.ILLUSTRATED)
                    .storageType(imageStorageService.getStorageType())
                    .storagePath(storagePath)
                    .mimeType("image/png")
                    .originalFilename(filename)
                    .capturedAt(OffsetDateTime.now())
                    .sortOrder(0)
                    .build();
            illustratedImage = imageRepository.save(illustratedImage);

            plant.setIllustratedImageAssetId(illustratedImage.getId());
            plantRepository.save(plant);

            log.info("Illustrated image saved for plant {}: id={}", plantId, illustratedImage.getId());
        } catch (Exception e) {
            log.error("Illustration generation failed for plant {}", plantId, e);
            // Non-fatal — the plant still functions without an illustrated image
        }
    }
}
