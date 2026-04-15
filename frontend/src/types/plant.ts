// TypeScript interfaces mirroring backend DTOs exactly

export type PlantStatus = "ACTIVE" | "ARCHIVED";
/** Where the plant primarily grows; OUTDOOR enables weather-informed reminder copy when lat/lon are set. */
export type PlantGrowingContext = "INDOOR" | "OUTDOOR";
export type AnalysisStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
export type AnalysisType = "REGISTRATION" | "REANALYSIS" | "PRUNING" | "REMINDER" | "INFO_PANEL";
export type ImageType = "ORIGINAL_UPLOAD" | "HEALTHY_REFERENCE" | "ILLUSTRATED" | "PRUNE_UPDATE";

export interface PlantImageDto {
  id: number;
  imageType: ImageType;
  url: string;
  mimeType: string | null;
  capturedAt: string | null;
  sortOrder: number;
}

export interface ReminderStateDto {
  wateringDue: boolean;
  wateringOverdue: boolean;
  fertilizerDue: boolean;
  pruningDue: boolean;
  healthAttentionNeeded: boolean;
  goalAttentionNeeded: boolean;
  nextWateringInstruction: string | null;
  nextFertilizerInstruction: string | null;
  nextPruningInstruction: string | null;
  /** Short outdoor weather context line from the reminder job (null for indoor or when weather is unavailable). */
  weatherCareNote: string | null;
  lastComputedAt: string;
}

export interface AnalysisSummaryDto {
  id: number;
  analysisType: AnalysisType;
  status: AnalysisStatus;
  genus: string | null;
  species: string | null;
  variety: string | null;
  scientificName: string | null;
  className: string | null;
  confidence: string | null;
  nativeRegions: string[] | null;
  lightNeeds: string | null;
  lightGeneralGuidance: string | null;
  placementGuidance: string | null;
  placementGeneralGuidance: string | null;
  wateringGuidance: string | null;
  wateringAmount: string | null;
  wateringFrequency: string | null;
  fertilizerGuidance: string | null;
  fertilizerType: string | null;
  fertilizerFrequency: string | null;
  pruningGuidance: string | null;
  pruningActionSummary: string | null;
  pruningGeneralGuidance: string | null;
  propagationInstructions: string | null;
  healthDiagnosis: string | null;
  goalSuggestions: string | null;
  speciesOverview: string | null;
  uses: string[] | null;
  completedAt: string | null;
  failureReason: string | null;
}

export interface PlantListItemResponse {
  id: number;
  name: string | null;
  genus: string | null;
  species: string | null;
  speciesLabel: string | null;
  displayLabel: string;
  illustratedImage: PlantImageDto | null;
  originalImage: PlantImageDto | null;
  reminderState: ReminderStateDto | null;
  status: PlantStatus;
  analysisStatus: AnalysisStatus | null;
}

export interface PlantHistoryEntryDto {
  id: number;
  /** JOURNAL | WATERING | FERTILIZER | PRUNE — stable keys with id across tables */
  entryKind?: string;
  noteText: string | null;
  image: PlantImageDto | null;
  createdAt: string;
}

/** Per-local-day narrative from the latest history summary job (newest day first). */
export interface HistoryDailyDigestDto {
  day: string;
  digest: string;
}

export interface PlantDetailResponse {
  id: number;
  name: string | null;
  genus: string | null;
  species: string | null;
  variety: string | null;
  speciesLabel: string | null;
  location: string | null;
  goalsText: string | null;
  geoCountry: string | null;
  geoState: string | null;
  geoCity: string | null;
  /** Omitted by older APIs; treated as INDOOR when missing. */
  growingContext?: PlantGrowingContext;
  latitude?: number | null;
  longitude?: number | null;
  status: PlantStatus;
  illustratedImage: PlantImageDto | null;
  originalImages: PlantImageDto[];
  healthyReferenceImages: PlantImageDto[];
  pruneUpdateImages: PlantImageDto[];
  latestAnalysis: AnalysisSummaryDto | null;
  reminderState: ReminderStateDto | null;
  hasActiveJobs: boolean;
  historyEntries: PlantHistoryEntryDto[];
  historySummaryText: string | null;
  /** Present after a successful digest-style summary; when non-empty, History uses these instead of mingling raw entries with parsed summary text. */
  historyDailyDigests?: HistoryDailyDigestDto[];
  historySummaryCompletedAt: string | null;
  /** Present when the latest history-summary job failed (or omitted by older APIs). */
  historySummaryError?: string | null;
  /** Omitted by older APIs; when false, the backend will reject "Generate summary" until there is journal or care data. */
  historySummaryEligible?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RequestHistorySummaryResponse {
  analysisId: number;
  plantId: number;
  status: string;
  createdAt: string;
}

export interface CreatePlantResponse {
  plantId: number;
  status: PlantStatus;
  createdAt: string;
  analysisId: number;
}

export interface PlantReminderResponse {
  plantId: number;
  plantName: string | null;
  speciesLabel: string | null;
  illustratedImage: PlantImageDto | null;
  isDue: boolean;
  isOverdue: boolean;
  instruction: string | null;
}

export interface RequestPruningAnalysisResponse {
  analysisId: number;
  plantId: number;
  status: AnalysisStatus;
  imagesUploaded: number;
  createdAt: string;
}

export interface RequestReanalysisResponse {
  analysisId: number;
  plantId: number;
  status: AnalysisStatus;
  createdAt: string;
}

export interface RecordWateringRequest {
  wateredAt: string;
  notes?: string;
}

export interface RecordFertilizerRequest {
  fertilizedAt: string;
  fertilizerType?: string;
  notes?: string;
}
