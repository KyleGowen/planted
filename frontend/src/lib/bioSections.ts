import type {
  AnalysisSummaryDto,
  BioSectionDto,
  BioSectionKey,
  PlantDetailResponse,
} from "@/types/plant";

/**
 * Reads a single bio section from the decomposed `bioSections` map, falling back
 * to the legacy monolithic `latestAnalysis` fields when the cache row is missing
 * (e.g. plants registered before the decomposition, before the one-time backfill
 * has run). Keeps call sites terse and consistent.
 *
 * The backend `PlantMapper` already backfills `bioSections[key]` from
 * `latestAnalysis` when no cache row exists, but the fallback path here is
 * defensive — older API builds that haven't shipped bioSections yet still work.
 */
export interface BioSectionView<T> {
  content: T | null;
  status: BioSectionDto["status"] | null;
  isRefreshing: boolean;
  generatedAt: string | null;
}

function emptyView<T>(): BioSectionView<T> {
  return { content: null, status: null, isRefreshing: false, generatedAt: null };
}

function readSection<T>(
  plant: PlantDetailResponse,
  key: BioSectionKey
): BioSectionView<T> {
  const row = plant.bioSections?.[key];
  if (!row) return emptyView<T>();
  return {
    content: (row.content as T | null) ?? null,
    status: row.status,
    isRefreshing: row.isRefreshing,
    generatedAt: row.generatedAt,
  };
}

export interface SpeciesIdContent {
  className?: string | null;
  taxonomicFamily?: string | null;
  genus?: string | null;
  species?: string | null;
  variety?: string | null;
  confidence?: string | null;
  nativeRegions?: string[] | null;
}

export interface HealthAssessmentContent {
  diagnosis?: string | null;
  severity?: string | null;
  signs?: string[] | null;
  checks?: string[] | null;
}

export interface SpeciesDescriptionContent {
  overview?: string | null;
  uses?: string[] | null;
}

export interface WaterCareContent {
  amount?: string | null;
  frequency?: string | null;
  guidance?: string | null;
}

export interface FertilizerCareContent {
  type?: string | null;
  frequency?: string | null;
  guidance?: string | null;
}

export interface PruningCareContent {
  actionSummary?: string | null;
  guidance?: string | null;
  generalGuidance?: string | null;
}

export interface LightCareContent {
  needs?: string | null;
  generalGuidance?: string | null;
}

export interface PlacementCareContent {
  guidance?: string | null;
  generalGuidance?: string | null;
}

export interface HistorySummaryContent {
  summary?: string | null;
}

export function getSpeciesIdSection(plant: PlantDetailResponse): BioSectionView<SpeciesIdContent> {
  const view = readSection<SpeciesIdContent>(plant, "SPECIES_ID");
  if (view.content) return view;
  const a = plant.latestAnalysis;
  if (!a) return view;
  return {
    ...view,
    content: {
      className: a.className,
      taxonomicFamily: a.taxonomicFamily,
      genus: a.genus,
      species: a.species,
      variety: a.variety,
      confidence: a.confidence,
      nativeRegions: a.nativeRegions,
    },
  };
}

export function getHealthSection(plant: PlantDetailResponse): BioSectionView<HealthAssessmentContent> {
  const view = readSection<HealthAssessmentContent>(plant, "HEALTH_ASSESSMENT");
  if (view.content) return view;
  const a = plant.latestAnalysis;
  if (!a?.healthDiagnosis) return view;
  return { ...view, content: { diagnosis: a.healthDiagnosis } };
}

export function getSpeciesDescriptionSection(
  plant: PlantDetailResponse
): BioSectionView<SpeciesDescriptionContent> {
  const view = readSection<SpeciesDescriptionContent>(plant, "SPECIES_DESCRIPTION");
  if (view.content) return view;
  const a = plant.latestAnalysis;
  if (!a) return view;
  return { ...view, content: { overview: a.speciesOverview, uses: a.uses } };
}

export function getWaterSection(plant: PlantDetailResponse): BioSectionView<WaterCareContent> {
  const view = readSection<WaterCareContent>(plant, "WATER_CARE");
  if (view.content) return view;
  const a = plant.latestAnalysis;
  if (!a) return view;
  return {
    ...view,
    content: { amount: a.wateringAmount, frequency: a.wateringFrequency, guidance: a.wateringGuidance },
  };
}

export function getFertilizerSection(plant: PlantDetailResponse): BioSectionView<FertilizerCareContent> {
  const view = readSection<FertilizerCareContent>(plant, "FERTILIZER_CARE");
  if (view.content) return view;
  const a = plant.latestAnalysis;
  if (!a) return view;
  return {
    ...view,
    content: { type: a.fertilizerType, frequency: a.fertilizerFrequency, guidance: a.fertilizerGuidance },
  };
}

export function getPruningSection(plant: PlantDetailResponse): BioSectionView<PruningCareContent> {
  const view = readSection<PruningCareContent>(plant, "PRUNING_CARE");
  if (view.content) return view;
  const a = plant.latestAnalysis;
  if (!a) return view;
  return {
    ...view,
    content: {
      actionSummary: a.pruningActionSummary,
      guidance: a.pruningGuidance,
      generalGuidance: a.pruningGeneralGuidance,
    },
  };
}

export function getLightSection(plant: PlantDetailResponse): BioSectionView<LightCareContent> {
  const view = readSection<LightCareContent>(plant, "LIGHT_CARE");
  if (view.content) return view;
  const a = plant.latestAnalysis;
  if (!a) return view;
  return { ...view, content: { needs: a.lightNeeds, generalGuidance: a.lightGeneralGuidance } };
}

export function getPlacementSection(plant: PlantDetailResponse): BioSectionView<PlacementCareContent> {
  const view = readSection<PlacementCareContent>(plant, "PLACEMENT_CARE");
  if (view.content) return view;
  const a = plant.latestAnalysis;
  if (!a) return view;
  return {
    ...view,
    content: { guidance: a.placementGuidance, generalGuidance: a.placementGeneralGuidance },
  };
}

export function getHistorySummarySection(
  plant: PlantDetailResponse
): BioSectionView<HistorySummaryContent> {
  return readSection<HistorySummaryContent>(plant, "HISTORY_SUMMARY");
}

/**
 * Flattens the nine bio sections into the shape of the legacy {@link AnalysisSummaryDto}
 * so existing UI bound to `plant.latestAnalysis.*` can be swapped over incrementally.
 * Missing fields fall back to `plant.latestAnalysis` values.
 */
export function flattenBioSectionsToAnalysis(
  plant: PlantDetailResponse
): AnalysisSummaryDto | null {
  const speciesId = getSpeciesIdSection(plant).content;
  const health = getHealthSection(plant).content;
  const description = getSpeciesDescriptionSection(plant).content;
  const water = getWaterSection(plant).content;
  const fertilizer = getFertilizerSection(plant).content;
  const pruning = getPruningSection(plant).content;
  const light = getLightSection(plant).content;
  const placement = getPlacementSection(plant).content;
  const legacy = plant.latestAnalysis;

  const anyContent =
    speciesId || health || description || water || fertilizer || pruning || light || placement;
  if (!anyContent && !legacy) return null;

  return {
    id: legacy?.id ?? 0,
    analysisType: legacy?.analysisType ?? "REGISTRATION",
    status: legacy?.status ?? "COMPLETED",
    genus: speciesId?.genus ?? legacy?.genus ?? null,
    species: speciesId?.species ?? legacy?.species ?? null,
    variety: speciesId?.variety ?? legacy?.variety ?? null,
    taxonomicFamily: speciesId?.taxonomicFamily ?? legacy?.taxonomicFamily ?? null,
    scientificName: legacy?.scientificName ?? null,
    className: speciesId?.className ?? legacy?.className ?? null,
    confidence: speciesId?.confidence ?? legacy?.confidence ?? null,
    nativeRegions: speciesId?.nativeRegions ?? legacy?.nativeRegions ?? null,
    lightNeeds: light?.needs ?? legacy?.lightNeeds ?? null,
    lightGeneralGuidance: light?.generalGuidance ?? legacy?.lightGeneralGuidance ?? null,
    placementGuidance: placement?.guidance ?? legacy?.placementGuidance ?? null,
    placementGeneralGuidance: placement?.generalGuidance ?? legacy?.placementGeneralGuidance ?? null,
    wateringGuidance: water?.guidance ?? legacy?.wateringGuidance ?? null,
    wateringAmount: water?.amount ?? legacy?.wateringAmount ?? null,
    wateringFrequency: water?.frequency ?? legacy?.wateringFrequency ?? null,
    fertilizerGuidance: fertilizer?.guidance ?? legacy?.fertilizerGuidance ?? null,
    fertilizerType: fertilizer?.type ?? legacy?.fertilizerType ?? null,
    fertilizerFrequency: fertilizer?.frequency ?? legacy?.fertilizerFrequency ?? null,
    pruningGuidance: pruning?.guidance ?? legacy?.pruningGuidance ?? null,
    pruningActionSummary: pruning?.actionSummary ?? legacy?.pruningActionSummary ?? null,
    pruningGeneralGuidance: pruning?.generalGuidance ?? legacy?.pruningGeneralGuidance ?? null,
    propagationInstructions: legacy?.propagationInstructions ?? null,
    healthDiagnosis: health?.diagnosis ?? legacy?.healthDiagnosis ?? null,
    goalSuggestions: legacy?.goalSuggestions ?? null,
    speciesOverview: description?.overview ?? legacy?.speciesOverview ?? null,
    uses: description?.uses ?? legacy?.uses ?? null,
    completedAt: legacy?.completedAt ?? null,
    failureReason: legacy?.failureReason ?? null,
  };
}

/**
 * True when at least one section we want to show is still generating or stale
 * (so the page should keep polling). Independent of the legacy analysis status.
 */
export function hasRefreshingBioSections(plant: PlantDetailResponse): boolean {
  const sections = plant.bioSections;
  if (!sections) return false;
  return Object.values(sections).some((row) => row?.isRefreshing);
}
