package com.planted.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planted.bio.BioSectionContext;
import com.planted.bio.PlantBioSectionStrategy;
import com.planted.client.OpenAiPlantClient;
import com.planted.entity.*;
import com.planted.queue.PlantJobMessage;
import com.planted.queue.PlantJobPublisher;
import com.planted.repository.PlantBioSectionRepository;
import com.planted.repository.PlantImageRepository;
import com.planted.repository.PlantRepository;
import com.planted.service.CareHistoryFormatter;
import com.planted.service.PlantReminderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Generic processor for bio-section refresh jobs. Dispatches to a
 * {@link PlantBioSectionStrategy} bean keyed by {@link PlantBioSectionKey}.
 * Cache-miss / fingerprint-hit is checked BEFORE the LLM call so a viewed
 * plant with fresh inputs costs zero tokens. On {@code SPECIES_ID} completion,
 * cascades invalidation + refresh jobs to every other section if (and only if)
 * the resolved species actually changed.
 */
@Slf4j
@Component
public class PlantBioSectionProcessor {

    @Value("${planted.storage.local-path:./data/images}")
    private String localStorageBasePath;

    private final PlantRepository plantRepository;
    private final PlantBioSectionRepository sectionRepository;
    private final PlantImageRepository imageRepository;
    private final OpenAiPlantClient openAiClient;
    private final PlantJobPublisher jobPublisher;
    private final CareHistoryFormatter careHistoryFormatter;
    private final ObjectMapper objectMapper;
    private final PlantReminderService plantReminderService;
    private final Map<PlantBioSectionKey, PlantBioSectionStrategy> strategies;

    public PlantBioSectionProcessor(
            PlantRepository plantRepository,
            PlantBioSectionRepository sectionRepository,
            PlantImageRepository imageRepository,
            OpenAiPlantClient openAiClient,
            PlantJobPublisher jobPublisher,
            CareHistoryFormatter careHistoryFormatter,
            ObjectMapper objectMapper,
            PlantReminderService plantReminderService,
            List<PlantBioSectionStrategy> strategies) {
        this.plantRepository = plantRepository;
        this.sectionRepository = sectionRepository;
        this.imageRepository = imageRepository;
        this.openAiClient = openAiClient;
        this.jobPublisher = jobPublisher;
        this.careHistoryFormatter = careHistoryFormatter;
        this.objectMapper = objectMapper;
        this.plantReminderService = plantReminderService;
        EnumMap<PlantBioSectionKey, PlantBioSectionStrategy> map = new EnumMap<>(PlantBioSectionKey.class);
        for (PlantBioSectionStrategy s : strategies) {
            map.put(s.key(), s);
        }
        this.strategies = map;
    }

    @Transactional
    public void process(PlantJobMessage message) {
        Long plantId = message.getPlantId();
        PlantBioSectionKey sectionKey = message.getSectionKey();
        if (sectionKey == null) {
            log.warn("Bio section job missing sectionKey for plant {}", plantId);
            return;
        }
        PlantBioSectionStrategy strategy = strategies.get(sectionKey);
        if (strategy == null) {
            log.warn("No strategy registered for section {}", sectionKey);
            return;
        }

        Plant plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));

        BioSectionContext ctx = buildContext(plant);

        // Sections other than SPECIES_ID need a resolved species name to be meaningful.
        if (sectionKey != PlantBioSectionKey.SPECIES_ID && isBlank(ctx.speciesName())) {
            log.info("Skipping {} for plant {} — species not yet resolved; will be enqueued by SPECIES_ID cascade",
                    plantId, sectionKey);
            return;
        }

        Map<String, String> inputs = strategy.inputs(plant, ctx);
        String fingerprint = fingerprint(strategy.promptKey(), inputs, strategy.key().requiresImage(),
                imagePathForFingerprint(plant));

        PlantBioSection row = sectionRepository
                .findByPlantIdAndSectionKey(plantId, sectionKey)
                .orElseGet(() -> PlantBioSection.builder()
                        .plantId(plantId)
                        .sectionKey(sectionKey)
                        .status(PlantBioSection.Status.PENDING)
                        .build());

        if (row.getStatus() == PlantBioSection.Status.COMPLETED
                && fingerprint.equals(row.getInputsFingerprint())
                && row.getGeneratedAt() != null
                && !isExpired(row)) {
            log.info("Bio section {} for plant {} is cache-hit (fingerprint match); skipping LLM call",
                    sectionKey, plantId);
            return;
        }

        row.setStatus(PlantBioSection.Status.PROCESSING);
        row.setLastError(null);
        sectionRepository.save(row);

        String previousSpeciesName = sectionKey == PlantBioSectionKey.SPECIES_ID
                ? extractSpeciesName(row)
                : null;

        try {
            List<String> imagesBase64 = List.of();
            List<String> mimeTypes = List.of();
            if (strategy.key().requiresImage()) {
                String[] img = loadPrimaryImageAsBase64(plantId);
                imagesBase64 = List.of(img[0]);
                mimeTypes = List.of(img[1]);
            }

            JsonNode result = openAiClient.generateBioSection(
                    strategy.promptKey(),
                    inputs,
                    strategy.schema(),
                    imagesBase64,
                    mimeTypes,
                    plantId);

            Map<String, Object> content = objectMapper.convertValue(
                    result, new TypeReference<Map<String, Object>>() {});

            row.setContentJsonb(content);
            row.setPromptKey(strategy.promptKey());
            row.setPromptVersion(openAiClient.resolveActivePromptVersion(strategy.promptKey()));
            row.setInputsFingerprint(fingerprint);
            row.setGeneratedAt(OffsetDateTime.now());
            row.setStatus(PlantBioSection.Status.COMPLETED);
            row.setLastError(null);
            sectionRepository.save(row);

            log.info("Bio section {} completed for plant {}", sectionKey, plantId);

            if (sectionKey == PlantBioSectionKey.HEALTH_ASSESSMENT
                    || sectionKey == PlantBioSectionKey.LIGHT_CARE
                    || sectionKey == PlantBioSectionKey.PLACEMENT_CARE) {
                plantReminderService.syncBioAttention(plantId);
            }

            if (sectionKey == PlantBioSectionKey.SPECIES_ID) {
                // Denormalize species onto Plant so list views and screensaver stay fast.
                applySpeciesToPlant(plant, content);
                plantRepository.save(plant);

                String newSpeciesName = newSpeciesName(content);
                boolean changed = !Objects.equals(newSpeciesName, previousSpeciesName);
                if (changed) {
                    log.info("Species changed for plant {} ({} -> {}); cascading refresh to dependent sections",
                            plantId, previousSpeciesName, newSpeciesName);
                    for (PlantBioSectionKey dep : PlantBioSectionKey.speciesDependent()) {
                        invalidateAndEnqueue(plantId, dep);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Bio section {} failed for plant {}", sectionKey, plantId, e);
            row.setStatus(PlantBioSection.Status.FAILED);
            row.setLastError(e.getMessage());
            sectionRepository.save(row);
        }
    }

    private void invalidateAndEnqueue(Long plantId, PlantBioSectionKey key) {
        PlantBioSection row = sectionRepository.findByPlantIdAndSectionKey(plantId, key)
                .orElseGet(() -> PlantBioSection.builder()
                        .plantId(plantId)
                        .sectionKey(key)
                        .status(PlantBioSection.Status.PENDING)
                        .build());
        row.setGeneratedAt(null);
        row.setInputsFingerprint(null);
        sectionRepository.save(row);
        jobPublisher.publish(PlantJobMessage.builder()
                .jobType(PlantJobMessage.JobType.PLANT_BIO_SECTION_REFRESH)
                .plantId(plantId)
                .sectionKey(key)
                .build());
    }

    private BioSectionContext buildContext(Plant plant) {
        String speciesName = plant.getSpeciesLabel();
        if (isBlank(speciesName)) {
            // Fall back to a previously cached SPECIES_ID row if denormalization has not yet caught up.
            PlantBioSection idRow = sectionRepository
                    .findByPlantIdAndSectionKey(plant.getId(), PlantBioSectionKey.SPECIES_ID)
                    .orElse(null);
            if (idRow != null && idRow.getContentJsonb() != null) {
                speciesName = newSpeciesName(idRow.getContentJsonb());
            }
        }
        String taxFamily = plant.getTaxonomicFamily();
        String nativeRegions = null;
        PlantBioSection idRow = sectionRepository
                .findByPlantIdAndSectionKey(plant.getId(), PlantBioSectionKey.SPECIES_ID)
                .orElse(null);
        if (idRow != null && idRow.getContentJsonb() != null) {
            Object regions = idRow.getContentJsonb().get("nativeRegions");
            if (regions instanceof List<?> list && !list.isEmpty()) {
                nativeRegions = String.join(", ", list.stream().map(Object::toString).toList());
            }
            if (isBlank(taxFamily)) {
                Object tf = idRow.getContentJsonb().get("taxonomicFamily");
                if (tf != null && !tf.toString().isBlank()) taxFamily = tf.toString();
            }
        }
        String geo = buildGeographicLocation(plant);
        String timeline = careHistoryFormatter.formatForLlm(plant.getId());
        String goals = plant.getGoalsText();
        return new BioSectionContext(speciesName, taxFamily, nativeRegions, geo, timeline, goals);
    }

    private static String buildGeographicLocation(Plant plant) {
        List<String> parts = new ArrayList<>();
        if (plant.getGeoCity() != null && !plant.getGeoCity().isBlank()) parts.add(plant.getGeoCity().trim());
        if (plant.getGeoState() != null && !plant.getGeoState().isBlank()) parts.add(plant.getGeoState().trim());
        if (plant.getGeoCountry() != null && !plant.getGeoCountry().isBlank()) parts.add(plant.getGeoCountry().trim());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /** Part of the fingerprint for vision sections: image identity (path or id) — not its full bytes. */
    private String imagePathForFingerprint(Plant plant) {
        Long pid = plant.getPrimaryImageId();
        return pid != null ? ("primary:" + pid) : "primary:none";
    }

    private boolean isExpired(PlantBioSection row) {
        if (row.getGeneratedAt() == null) return true;
        return row.getGeneratedAt().plus(row.getSectionKey().ttl()).isBefore(OffsetDateTime.now());
    }

    private String[] loadPrimaryImageAsBase64(Long plantId) {
        PlantImage image = imageRepository
                .findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(plantId, PlantImage.ImageType.ORIGINAL_UPLOAD)
                .orElseThrow(() -> new IllegalArgumentException("No image found for plant " + plantId));
        try {
            byte[] bytes;
            if (image.getStorageType() == PlantImage.StorageType.LOCAL) {
                Path fullPath = Paths.get(localStorageBasePath).toAbsolutePath().normalize()
                        .resolve(image.getStoragePath());
                bytes = Files.readAllBytes(fullPath);
            } else {
                throw new UnsupportedOperationException("S3 image loading in worker not yet implemented");
            }
            String mimeType = image.getMimeType() != null ? image.getMimeType() : "image/jpeg";
            return new String[]{Base64.getEncoder().encodeToString(bytes), mimeType};
        } catch (IOException e) {
            throw new RuntimeException("Failed to load image for bio section: " + image.getStoragePath(), e);
        }
    }

    /**
     * Pulls the species name out of a SPECIES_ID content map. Uses scientificName when
     * the Plant has not been denormalized, else reconstructs genus + species.
     */
    private static String newSpeciesName(Map<String, Object> content) {
        if (content == null) return null;
        String genus = asString(content.get("genus"));
        String species = asString(content.get("species"));
        if (!isBlank(genus) && !isBlank(species)) {
            return (genus.trim() + " " + species.trim()).trim();
        }
        if (!isBlank(genus)) return genus.trim();
        String className = asString(content.get("className"));
        if (!isBlank(className)) return className.trim();
        return null;
    }

    private static String extractSpeciesName(PlantBioSection row) {
        if (row == null || row.getContentJsonb() == null) return null;
        return newSpeciesName(row.getContentJsonb());
    }

    private static void applySpeciesToPlant(Plant plant, Map<String, Object> content) {
        if (content == null) return;
        String genus = asString(content.get("genus"));
        String species = asString(content.get("species"));
        String variety = asString(content.get("variety"));
        String taxonomicFamily = asString(content.get("taxonomicFamily"));
        String className = asString(content.get("className"));
        if (!isBlank(genus)) plant.setGenus(genus.trim());
        if (!isBlank(species)) plant.setSpecies(species.trim());
        if (!isBlank(variety)) plant.setVariety(variety.trim());
        if (!isBlank(taxonomicFamily)) plant.setTaxonomicFamily(taxonomicFamily.trim());
        if (!isBlank(className)) plant.setClassName(className.trim());
        String label = newSpeciesName(content);
        if (!isBlank(label)) plant.setSpeciesLabel(label);
    }

    private String fingerprint(String promptKey, Map<String, String> inputs, boolean withImage, String imageToken) {
        TreeMap<String, String> ordered = new TreeMap<>(inputs);
        StringBuilder sb = new StringBuilder();
        sb.append(promptKey).append('|');
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue() == null ? "" : e.getValue()).append('\n');
        }
        if (withImage) sb.append("image|").append(imageToken);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
