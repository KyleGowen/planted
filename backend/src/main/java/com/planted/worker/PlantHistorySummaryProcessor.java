package com.planted.worker;

import com.planted.client.OpenAiPlantClient;
import com.planted.client.PlantHistorySummarySchema;
import com.planted.entity.*;
import com.planted.queue.PlantJobMessage;
import com.planted.repository.*;
import com.planted.service.HistorySummaryDayZoneResolver;
import com.planted.service.PlantHistorySummaryPersistenceHelper;
import com.planted.service.UserPhysicalAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlantHistorySummaryProcessor {

    private static final int MAX_CARE_EVENTS_PER_TYPE = 20;
    private static final int MAX_JOURNAL_IMAGES = 8;
    private static final DateTimeFormatter PHOTO_CAPTION = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    @Value("${planted.storage.local-path:./data/images}")
    private String localStorageBasePath;

    private final PlantRepository plantRepository;
    private final PlantAnalysisRepository analysisRepository;
    private final PlantImageRepository imageRepository;
    private final PlantHistoryEntryRepository historyEntryRepository;
    private final PlantWateringEventRepository wateringEventRepository;
    private final PlantFertilizerEventRepository fertilizerEventRepository;
    private final PlantPruneEventRepository pruneEventRepository;
    private final OpenAiPlantClient openAiClient;
    private final PlantHistorySummaryPersistenceHelper persistenceHelper;
    private final UserPhysicalAddressService userPhysicalAddressService;
    private final HistorySummaryDayZoneResolver historyDayZoneResolver;

    public void process(PlantJobMessage message) {
        Long plantId = message.getPlantId();
        Long analysisId = message.getAnalysisId();

        PlantAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));

        if (analysis.getAnalysisType() != PlantAnalysis.AnalysisType.INFO_PANEL) {
            throw new IllegalArgumentException("Expected INFO_PANEL analysis, got " + analysis.getAnalysisType());
        }

        plantRepository.findById(plantId)
                .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));

        persistenceHelper.markProcessing(analysisId);

        try {
            Plant plant = plantRepository.findById(plantId).orElseThrow();
            List<PlantHistoryEntry> journalOldest = historyEntryRepository
                    .findByPlantIdOrderByCreatedAtAsc(plantId);

            List<PlantWateringEvent> waterings = wateringEventRepository
                    .findByPlantIdOrderByWateredAtDesc(plantId);
            List<PlantFertilizerEvent> ferts = fertilizerEventRepository
                    .findByPlantIdOrderByFertilizedAtDesc(plantId);
            List<PlantPruneEvent> prunes = pruneEventRepository
                    .findByPlantIdOrderByPrunedAtDesc(plantId);

            ZoneId dayZone = historyDayZoneResolver.resolveZone(plant.getLatitude(), plant.getLongitude());
            String groupedByDay = PlantHistorySummaryDayGroupBuilder.buildGroupedSection(
                    dayZone, journalOldest, waterings, ferts, prunes, MAX_CARE_EVENTS_PER_TYPE);
            String timelineCore = PlantHistorySummaryTimelineBuilder.buildTimelineText(
                    journalOldest, waterings, ferts, prunes, MAX_CARE_EVENTS_PER_TYPE);
            String timelineText = groupedByDay + "\n\n=== Detailed chronological log (reference; same facts) ===\n\n"
                    + timelineCore;

            Optional<PlantAnalysis> latestCare = analysisRepository
                    .findFirstByPlantIdAndAnalysisTypeInAndStatusOrderByCompletedAtDescIdDesc(
                            plantId,
                            List.of(PlantAnalysis.AnalysisType.REGISTRATION, PlantAnalysis.AnalysisType.REANALYSIS),
                            PlantAnalysis.AnalysisStatus.COMPLETED);

            String plantProfile = PlantHistorySummaryProfileBuilder.buildPlantProfile(
                    plant, latestCare.orElse(null));

            Optional<PlantImage> baselineOpt = resolveBaselineImage(plant);
            Long baselineImageId = baselineOpt.map(PlantImage::getId).orElse(null);

            List<PlantHistoryEntry> journalPhotos = journalOldest.stream()
                    .filter(e -> e.getImageId() != null)
                    .filter(e -> baselineImageId == null || !baselineImageId.equals(e.getImageId()))
                    .toList();
            if (journalPhotos.size() > MAX_JOURNAL_IMAGES) {
                journalPhotos = journalPhotos.subList(journalPhotos.size() - MAX_JOURNAL_IMAGES, journalPhotos.size());
            }

            BaselineAttachResult baselineResult = attachBaselineImage(baselineOpt.orElse(null));

            List<String> imagesBase64 = new ArrayList<>(baselineResult.imagesBase64());
            List<String> mimeTypes = new ArrayList<>(baselineResult.mimeTypes());
            for (PlantHistoryEntry entry : journalPhotos) {
                PlantImage img = imageRepository.findById(entry.getImageId())
                        .orElseThrow(() -> new IllegalArgumentException("History image not found: " + entry.getImageId()));
                String[] encoded = loadImageAsBase64(img);
                imagesBase64.add(encoded[0]);
                mimeTypes.add(encoded[1]);
            }

            PlantImage baselineForCaption = baselineResult.baselineAttached() ? baselineOpt.orElse(null) : null;
            timelineText = appendVisionCaptionSection(timelineText, baselineForCaption, journalPhotos);

            String imageCountLabel = buildImageCountLabel(baselineResult.baselineAttached(), journalPhotos.size());
            String speciesLabel = plant.getSpeciesLabel() != null ? plant.getSpeciesLabel()
                    : PlantHistorySummaryProcessor.buildSpeciesLabel(plant.getGenus(), plant.getSpecies());

            log.info("Running plant history summary for plant {} ({} image(s) to model)",
                    plantId, imagesBase64.size());

            String ownerAddress = userPhysicalAddressService.resolveAddressForPlant(plant)
                    .orElse(null);
            PlantHistorySummarySchema result = openAiClient.summarizePlantHistory(
                    plantProfile,
                    baselineResult.baselinePhotoNote(),
                    timelineText,
                    plant.getName(),
                    speciesLabel,
                    imageCountLabel,
                    ownerAddress,
                    imagesBase64,
                    mimeTypes,
                    plantId,
                    analysisId);

            String flattened = result.flattenForStorage();
            if (flattened.isBlank()) {
                throw new IllegalStateException("Model returned an empty summary.");
            }

            Map<String, Object> raw = new HashMap<>();
            raw.put("dailyDigests", result.getDailyDigests());
            raw.put("summary", flattened);
            persistenceHelper.markCompleted(analysisId, flattened, raw);

            log.info("Plant history summary completed for plant {}", plantId);
        } catch (Throwable t) {
            if (t instanceof ThreadDeath) {
                throw (ThreadDeath) t;
            }
            log.error("Plant history summary failed for plant {}", plantId, t);
            String msg = t.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = t.toString();
            }
            persistenceHelper.markFailed(analysisId, msg);
        }
    }

    private Optional<PlantImage> resolveBaselineImage(Plant plant) {
        if (plant.getPrimaryImageId() != null) {
            return imageRepository.findById(plant.getPrimaryImageId())
                    .filter(img -> plant.getId().equals(img.getPlantId()));
        }
        return imageRepository.findFirstByPlantIdAndImageTypeOrderByCreatedAtDesc(
                plant.getId(), PlantImage.ImageType.ORIGINAL_UPLOAD);
    }

    private BaselineAttachResult attachBaselineImage(PlantImage baseline) {
        if (baseline == null) {
            return new BaselineAttachResult(List.of(), List.of(), false, "");
        }
        if (baseline.getStorageType() != PlantImage.StorageType.LOCAL) {
            String note = "Registration/baseline photo uses "
                    + baseline.getStorageType()
                    + " storage; it was not embedded. Use the timeline text and any journal images only.";
            log.warn("Skipping baseline image {} for history summary: {}", baseline.getId(), note);
            return new BaselineAttachResult(List.of(), List.of(), false, note);
        }
        try {
            String[] encoded = loadImageAsBase64(baseline);
            return new BaselineAttachResult(
                    List.of(encoded[0]),
                    List.of(encoded[1]),
                    true,
                    "");
        } catch (RuntimeException e) {
            log.warn("Failed to load baseline image {} for history summary", baseline.getId(), e);
            return new BaselineAttachResult(List.of(), List.of(), false,
                    "Registration/baseline photo could not be loaded for this summary.");
        }
    }

    private static String buildImageCountLabel(boolean baselineAttached, int journalCount) {
        if (baselineAttached && journalCount > 0) {
            return "1 registration/baseline photo and " + journalCount + " journal photo(s)";
        }
        if (baselineAttached) {
            return "1 registration/baseline photo";
        }
        if (journalCount > 0) {
            return journalCount + " journal photo(s)";
        }
        return "";
    }

    private static String appendVisionCaptionSection(
            String timelineCore,
            PlantImage baselineImage,
            List<PlantHistoryEntry> journalPhotoEntries) {

        boolean hasBaselineCaption = baselineImage != null;
        if (!hasBaselineCaption && journalPhotoEntries.isEmpty()) {
            return timelineCore;
        }
        StringBuilder sb = new StringBuilder(timelineCore);
        sb.append("\n\n=== Photos attached (vision context; match journal lines above) ===\n");
        int n = 1;
        if (hasBaselineCaption) {
            OffsetDateTime when = baselineImage.getCapturedAt() != null
                    ? baselineImage.getCapturedAt()
                    : baselineImage.getCreatedAt();
            sb.append("Photo ").append(n++).append(": registration / baseline (captured around ")
                    .append(when.format(PHOTO_CAPTION)).append(")\n");
        }
        for (PlantHistoryEntry e : journalPhotoEntries) {
            sb.append("Photo ").append(n++).append(": journal — taken around ")
                    .append(e.getCreatedAt().format(PHOTO_CAPTION)).append('\n');
        }
        return sb.toString();
    }

    private String[] loadImageAsBase64(PlantImage image) {
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
            throw new RuntimeException("Failed to load history image: " + image.getStoragePath(), e);
        }
    }

    private static String buildSpeciesLabel(String genus, String species) {
        if (genus == null && species == null) {
            return null;
        }
        if (species == null) {
            return genus;
        }
        if (genus == null) {
            return species;
        }
        return genus + " " + species;
    }

    private record BaselineAttachResult(
            List<String> imagesBase64,
            List<String> mimeTypes,
            boolean baselineAttached,
            String baselinePhotoNote) {
    }
}
