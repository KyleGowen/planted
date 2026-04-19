package com.planted.queue;

import com.planted.entity.PlantBioSectionKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlantJobMessage {

    private JobType jobType;
    private Long plantId;
    private Long analysisId;
    private List<Long> imageIds;
    /** Section to refresh when {@link JobType#PLANT_BIO_SECTION_REFRESH}. */
    private PlantBioSectionKey sectionKey;

    public enum JobType {
        PLANT_REGISTRATION_ANALYSIS,
        PLANT_ILLUSTRATION_GENERATION,
        PLANT_REMINDER_RECOMPUTE,
        PRUNING_ANALYSIS,
        PLANT_REANALYSIS,
        HEALTHY_REFERENCE_IMAGE_FETCH,
        PLANT_HISTORY_SUMMARY,
        /** @deprecated superseded by {@link #PLANT_BIO_SECTION_REFRESH} with {@code sectionKey=PLACEMENT_CARE}. */
        @Deprecated
        PLACEMENT_NOTES_SUMMARY,
        PLANT_BIO_SECTION_REFRESH
    }
}
