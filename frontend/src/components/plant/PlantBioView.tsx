"use client";

import { useState, useMemo, useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Droplets, Leaf, Scissors, Archive,
  ChevronLeft, ChevronRight, Globe, ScrollText, Sun, MapPin, Utensils, Pencil, Check, X, RefreshCw,
  Clock, HeartPulse,
} from "lucide-react";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PlantStatusCard } from "@/components/plant/PlantStatusCard";
import { CareObservationInput } from "@/components/plant/CareObservationInput";
import { ReminderIconRow } from "@/components/plant/ReminderIconRow";
import {
  archivePlant,
  recordWatering,
  recordFertilizer,
  recordPrune,
  updatePlantName,
  updatePlantGrowing,
  updatePlantPlacement,
  requestReanalysis,
  addHistoryNote,
  uploadPlantPhoto,
  requestHistorySummary,
  getUserLocation,
} from "@/lib/api";
import type {
  PlantDetailResponse,
  PlantGrowingContext,
  PlantHistoryEntryDto,
  PlantImageDto,
} from "@/types/plant";
import { SpeciesOverviewProse } from "@/components/plant/SpeciesOverviewProse";
import { CareTopicAccordion } from "@/components/plant/CareTopicAccordion";
import {
  buildHistoryDayTiles,
  formatDayTileTitle,
  type TimelineRow,
} from "@/lib/historyDayTimeline";
import {
  flattenBioSectionsToAnalysis,
  getHealthSection,
  getSpeciesDescriptionSection,
  getSpeciesIdSection,
  getFertilizerSection,
  getLightSection,
  getPlacementSection,
  getPruningSection,
  getWaterSection,
} from "@/lib/bioSections";
import { plantImageSrc } from "@/lib/plantMediaUrl";

interface PlantBioViewProps {
  plant: PlantDetailResponse;
  /** When true, render a presentational-only view: no pencils, dialogs, action buttons, or note input. */
  readOnly?: boolean;
  /** Called after a successful archive mutation so the parent can navigate away. Ignored in readOnly mode. */
  onArchived?: () => void;
  /**
   * Screensaver-only: replaces the Care panel with a second image panel showing
   * this image (a random history photo or reference image picked by the parent).
   * Ignored outside of {@link readOnly} mode.
   */
  screensaverAuxImage?: PlantImageDto | null;
}

export function PlantBioView({
  plant,
  readOnly = false,
  onArchived,
  screensaverAuxImage = null,
}: PlantBioViewProps) {
  const plantId = plant.id;
  const queryClient = useQueryClient();

  const [historyIndex, setHistoryIndex] = useState(0);
  const [editingName, setEditingName] = useState(false);
  const [nameInput, setNameInput] = useState("");
  const [editingGrowing, setEditingGrowing] = useState(false);
  const [growingCtx, setGrowingCtx] = useState<PlantGrowingContext>("INDOOR");
  const [growingPlacementInput, setGrowingPlacementInput] = useState("");
  const [placementDialogOpen, setPlacementDialogOpen] = useState(false);
  const [placementInput, setPlacementInput] = useState("");

  const { data: userLocation } = useQuery({
    queryKey: ["userLocation"],
    queryFn: getUserLocation,
  });
  const hasUserLocation = Boolean(userLocation?.address?.trim());

  const archiveMutation = useMutation({
    mutationFn: () => archivePlant(plantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      onArchived?.();
    },
  });

  const waterMutation = useMutation({
    mutationFn: (wateredAt: string) => recordWatering(plantId, { wateredAt }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plants"] });
    },
  });

  const fertMutation = useMutation({
    mutationFn: () => recordFertilizer(plantId, { fertilizedAt: new Date().toISOString() }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plant", plantId] }),
  });

  const pruneMutation = useMutation({
    mutationFn: () => recordPrune(plantId, new Date().toISOString()),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plant", plantId] }),
  });

  const nameMutation = useMutation({
    mutationFn: (name: string | null) => updatePlantName(plantId, name),
    onSuccess: () => {
      setEditingName(false);
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plants"] });
    },
  });

  const growingMutation = useMutation({
    mutationFn: (body: { growingContext: PlantGrowingContext }) =>
      updatePlantGrowing(plantId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plants"] });
    },
  });

  const reanalysisMutation = useMutation({
    mutationFn: () => requestReanalysis(plantId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plant", plantId] }),
  });

  const placementMutation = useMutation({
    mutationFn: (location: string | null) => updatePlantPlacement(plantId, location),
    onMutate: async (location) => {
      await queryClient.cancelQueries({ queryKey: ["plant", plantId] });
      const previous = queryClient.getQueryData<PlantDetailResponse>(["plant", plantId]);
      if (previous) {
        queryClient.setQueryData<PlantDetailResponse>(["plant", plantId], {
          ...previous,
          location,
          placementNotesSummary: null,
          hasActiveJobs: true,
        });
      }
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.previous) {
        queryClient.setQueryData(["plant", plantId], ctx.previous);
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["plant", plantId] }),
  });

  const addNoteMutation = useMutation({
    mutationFn: (noteText: string) => addHistoryNote(plantId, noteText),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      queryClient.invalidateQueries({ queryKey: ["activity"] });
      addNoteMutation.reset();
    },
  });

  // Camera button uploads a new primary photo: it becomes the hero, the previous one
  // slides into "Photo history", a history entry is auto-recorded, and the health
  // bio section is refreshed. See PlantCommandService.uploadPrimaryPhoto.
  const addImageMutation = useMutation({
    mutationFn: ({ image, noteText }: { image: File; noteText?: string }) =>
      uploadPlantPhoto(plantId, image, noteText),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      addImageMutation.reset();
    },
  });

  const historySummaryMutation = useMutation({
    mutationFn: () => requestHistorySummary(plantId),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
    },
  });
  const resetHistorySummaryMutation = historySummaryMutation.reset;

  // After refetch, prefer server history state so a stale POST error does not mask a completed summary.
  // Dependency count must stay fixed (React 19); use primitives + stable reset, not `plant` / mutation objects.
  useEffect(() => {
    const hasText = Boolean(plant?.historySummaryText?.trim());
    const hasServerError = Boolean(plant?.historySummaryError);
    if (!hasText && !hasServerError) return;
    resetHistorySummaryMutation();
  }, [
    plant?.id,
    plant?.historySummaryText,
    plant?.historySummaryError,
    resetHistorySummaryMutation,
  ]);

  function startEditName() {
    setNameInput(plant?.name ?? "");
    setEditingName(true);
  }

  function saveName() {
    const trimmed = nameInput.trim();
    nameMutation.mutate(trimmed === "" ? null : trimmed);
  }

  function cancelEditName() {
    setEditingName(false);
  }

  function startEditGrowing() {
    growingMutation.reset();
    placementMutation.reset();
    setGrowingCtx(plant?.growingContext ?? "INDOOR");
    setGrowingPlacementInput(plant?.location ?? "");
    setEditingGrowing(true);
  }

  async function saveGrowing() {
    const currentContext = plant?.growingContext ?? "INDOOR";
    const currentLocation = plant?.location ?? "";
    const nextLocationRaw = growingPlacementInput.trim();
    const nextLocation = nextLocationRaw === "" ? null : nextLocationRaw;

    try {
      const tasks: Promise<unknown>[] = [];
      if (growingCtx !== currentContext) {
        tasks.push(growingMutation.mutateAsync({ growingContext: growingCtx }));
      }
      if (currentLocation !== (nextLocation ?? "")) {
        tasks.push(placementMutation.mutateAsync(nextLocation));
      }
      if (tasks.length > 0) {
        await Promise.all(tasks);
      }
      setEditingGrowing(false);
    } catch {
      /* keep edit mode open; errors surface via mutation.error */
    }
  }

  function cancelEditGrowing() {
    setEditingGrowing(false);
  }

  const analysis = flattenBioSectionsToAnalysis(plant);
  const speciesIdSection = getSpeciesIdSection(plant);
  const speciesDescriptionSection = getSpeciesDescriptionSection(plant);
  const waterSection = getWaterSection(plant);
  const fertilizerSection = getFertilizerSection(plant);
  const pruningSection = getPruningSection(plant);
  const lightSection = getLightSection(plant);
  const placementSection = getPlacementSection(plant);
  const healthSection = getHealthSection(plant);
  const historyImages = [...plant.originalImages, ...plant.pruneUpdateImages];
  const referenceImages = plant.healthyReferenceImages;
  const mainImage = plant.illustratedImage ?? plant.originalImages[0] ?? null;

  const HISTORY_PAGE = 4;
  const maxIndex = Math.max(0, historyImages.length - HISTORY_PAGE);

  const displayName = plant.name ?? plant.speciesLabel ?? "Identifying…";
  const speciesLine =
    plant.speciesLabel?.trim() ||
    analysis?.scientificName?.trim() ||
    [plant.genus, plant.species].filter(Boolean).join(" ");
  const hasLocationNotes = Boolean(plant.location?.trim());
  const placementSummaryLine = plant.placementNotesSummary?.trim() || null;
  const placementSummaryPending =
    placementMutation.isPending
    || (hasLocationNotes && !placementSummaryLine && plant.hasActiveJobs);

  function openPlacementDialog() {
    placementMutation.reset();
    setPlacementInput(plant?.location ?? "");
    setPlacementDialogOpen(true);
  }

  async function submitPlacementDialog() {
    try {
      const trimmed = placementInput.trim();
      await placementMutation.mutateAsync(trimmed === "" ? null : trimmed);
      setPlacementDialogOpen(false);
    } catch {
      /* error shown via placementMutation.error */
    }
  }

  // Extracted so the right side can be laid out either as a flex-col (edit
  // mode) or as a 2-column grid (screensaver) without duplicating JSX. In the
  // screensaver branch the header/status sit only above the middle panel so
  // the second image can span the full column height and match the hero.
  const headerBlock = (
    <div className="flex items-start justify-between gap-4">
      {/* Left: name + species */}
      <div className="min-w-0">
        {editingName && !readOnly ? (
          <div className="flex items-center gap-2">
            <input
              autoFocus
              type="text"
              value={nameInput}
              onChange={(e) => setNameInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") saveName();
                if (e.key === "Escape") cancelEditName();
              }}
              placeholder={speciesLine || "Plant name"}
              className="text-3xl font-semibold tracking-tight text-stone-800 bg-transparent border-b-2 border-stone-400 focus:border-stone-700 outline-none w-full plant-name"
            />
            <button
              onClick={saveName}
              disabled={nameMutation.isPending}
              className="p-1 rounded-full text-green-600 hover:bg-green-50 transition-colors flex-shrink-0"
              title="Save name"
            >
              <Check size={16} />
            </button>
            <button
              onClick={cancelEditName}
              className="p-1 rounded-full text-stone-400 hover:bg-stone-100 transition-colors flex-shrink-0"
              title="Cancel"
            >
              <X size={16} />
            </button>
          </div>
        ) : (
          <div className="group flex items-center gap-2">
            <h1 className="text-3xl font-semibold tracking-tight text-stone-800 plant-name">
              {displayName}
            </h1>
            {!readOnly && (
              <button
                onClick={startEditName}
                className="p-1 rounded-full text-stone-300 hover:text-stone-500 hover:bg-stone-100 transition-colors opacity-0 group-hover:opacity-100 flex-shrink-0"
                title="Edit name"
              >
                <Pencil size={13} />
              </button>
            )}
          </div>
        )}
        {speciesLine && (
          <p className="text-sm text-stone-400 italic mt-0.5 species-label">{speciesLine}</p>
        )}
      </div>

      {/* Right: placement + compact growing / weather */}
      <div className="flex flex-col items-end gap-1.5 flex-shrink-0 text-right max-w-[min(22rem,46vw)]">
        {(plant.geoCity || plant.geoState || plant.geoCountry) && (
          <div className="flex flex-col items-end gap-0.5">
            <p className="text-sm text-stone-400 flex items-center justify-end gap-1">
              <MapPin size={11} className="flex-shrink-0" />
              {[plant.geoCity, plant.geoState, plant.geoCountry].filter(Boolean).join(", ")}
            </p>
          </div>
        )}
        <div className="group flex items-start justify-end gap-1.5 text-right">
          {!editingGrowing && !readOnly && (
            <button
              type="button"
              onClick={startEditGrowing}
              className="p-0.5 rounded-full text-stone-300 hover:text-stone-500 hover:bg-stone-100 transition-colors opacity-0 group-hover:opacity-100 flex-shrink-0"
              title="Edit growing environment"
            >
              <Pencil size={13} />
            </button>
          )}
          <div className="min-w-0 text-xs text-stone-500 leading-snug">
            {editingGrowing && !readOnly ? (
              (() => {
                const busy = growingMutation.isPending || placementMutation.isPending;
                const growingError =
                  growingMutation.error instanceof Error
                    ? growingMutation.error.message
                    : null;
                const placementError =
                  placementMutation.error instanceof Error
                    ? placementMutation.error.message
                    : null;
                return (
                  <div className="flex flex-col items-end gap-1.5">
                    <div className="flex items-center justify-end gap-1.5">
                      <select
                        autoFocus
                        value={growingCtx}
                        onChange={(e) =>
                          setGrowingCtx(e.target.value as PlantGrowingContext)
                        }
                        onKeyDown={(e) => {
                          if (e.key === "Enter") void saveGrowing();
                          if (e.key === "Escape") cancelEditGrowing();
                        }}
                        disabled={busy}
                        className="rounded-md border border-stone-300 bg-white px-1.5 py-0.5 text-xs text-stone-800 outline-none focus:border-stone-500"
                      >
                        <option value="INDOOR">Indoor</option>
                        <option value="OUTDOOR">Outdoor</option>
                      </select>
                      <button
                        type="button"
                        onClick={() => void saveGrowing()}
                        disabled={busy}
                        className="p-0.5 rounded-full text-green-600 hover:bg-green-50 transition-colors flex-shrink-0 disabled:opacity-40"
                        title="Save"
                      >
                        <Check size={14} />
                      </button>
                      <button
                        type="button"
                        onClick={cancelEditGrowing}
                        disabled={busy}
                        className="p-0.5 rounded-full text-stone-400 hover:bg-stone-100 transition-colors flex-shrink-0 disabled:opacity-40"
                        title="Cancel"
                      >
                        <X size={14} />
                      </button>
                    </div>
                    <textarea
                      value={growingPlacementInput}
                      onChange={(e) =>
                        setGrowingPlacementInput(e.target.value.slice(0, 800))
                      }
                      onKeyDown={(e) => {
                        if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
                          e.preventDefault();
                          void saveGrowing();
                        }
                        if (e.key === "Escape") cancelEditGrowing();
                      }}
                      rows={3}
                      disabled={busy}
                      placeholder="Placement notes (e.g. east window, morning sun)"
                      className="w-full rounded-md border border-stone-300 bg-white px-2 py-1.5 text-xs text-stone-800 outline-none focus:border-stone-500 resize-y min-h-[3.5rem] text-left"
                    />
                    <p className="text-[10px] text-stone-400 tabular-nums self-end">
                      {growingPlacementInput.length}/800
                    </p>
                    {(growingError || placementError) && (
                      <p
                        className="text-[11px] text-red-600 leading-snug text-left"
                        role="alert"
                      >
                        {growingError || placementError}
                      </p>
                    )}
                  </div>
                );
              })()
            ) : (
              <>
                <p>
                  {(plant.growingContext ?? "INDOOR") === "OUTDOOR" ? "Outdoor" : "Indoor"}
                  {(plant.growingContext ?? "INDOOR") === "OUTDOOR"
                    && !plant.geoCity
                    && !hasUserLocation
                    ? " · Add your location on the Plants page for weather reminders"
                    : ""}
                </p>
                {placementSummaryLine ? (
                  <p className="mt-0.5 text-xs text-stone-500 italic leading-snug">
                    {placementSummaryLine}
                  </p>
                ) : hasLocationNotes ? (
                  <p className="mt-0.5 text-xs text-stone-400 italic leading-snug">
                    {placementSummaryPending
                      ? "Summarizing placement notes…"
                      : "Placement summary unavailable."}
                  </p>
                ) : readOnly ? null : (
                  <button
                    type="button"
                    onClick={openPlacementDialog}
                    className="mt-0.5 text-xs text-stone-400 hover:text-stone-600 italic leading-snug underline-offset-2 hover:underline"
                    title="Add placement notes"
                  >
                    Add placement notes
                  </button>
                )}
                {plant.reminderState?.weatherCareNote && (
                  <p className="mt-1 text-xs text-stone-500 leading-snug">
                    {plant.reminderState.weatherCareNote}
                  </p>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );

  const statusBlock = <PlantStatusCard status={analysis?.status ?? null} />;

  const placementDialog = !readOnly ? (
    <Dialog
      open={placementDialogOpen}
      onOpenChange={(open) => {
        if (!open && !placementMutation.isPending) setPlacementDialogOpen(false);
      }}
    >
      <DialogContent
        className="sm:max-w-md"
        showCloseButton={!placementMutation.isPending}
      >
        <DialogHeader>
          <DialogTitle>Placement notes</DialogTitle>
        </DialogHeader>
        <p className="text-xs text-stone-500 leading-snug">
          These notes are sent to the model as context for placement guidance. Saving runs a full refresh so
          recommendations can update.
        </p>
        <label className="block text-xs text-stone-500">
          <span className="sr-only">Placement notes</span>
          <textarea
            value={placementInput}
            onChange={(e) => setPlacementInput(e.target.value.slice(0, 800))}
            rows={4}
            className="mt-1 w-full rounded-md border border-stone-200 bg-white px-2 py-1.5 text-sm text-stone-800 resize-y min-h-[5rem]"
            placeholder="e.g. Living room, east window, morning sun…"
            disabled={placementMutation.isPending}
          />
        </label>
        <p className="text-[11px] text-stone-400 tabular-nums">{placementInput.length}/800</p>
        {placementMutation.error instanceof Error && (
          <p className="text-xs text-red-600 leading-snug" role="alert">
            {placementMutation.error.message}
          </p>
        )}
        <div className="flex justify-end gap-2 pt-1">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setPlacementDialogOpen(false)}
            disabled={placementMutation.isPending}
          >
            Cancel
          </Button>
          <Button
            type="button"
            size="sm"
            onClick={() => void submitPlacementDialog()}
            disabled={placementMutation.isPending}
          >
            {placementMutation.isPending ? "Saving…" : "Save"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  ) : null;

  const speciesPanelBlock = (
    <SpeciesPanel
      analysis={analysis}
      speciesIdRefreshing={speciesIdSection.isRefreshing}
      speciesDescriptionRefreshing={speciesDescriptionSection.isRefreshing}
      historyEntries={plant.historyEntries ?? []}
      heroImageUrl={mainImage ? plantImageSrc(mainImage.url) : null}
      historyDailyDigests={plant.historyDailyDigests ?? []}
      historySummaryText={plant.historySummaryText}
      historySummaryCompletedAt={plant.historySummaryCompletedAt}
      historySummaryError={plant.historySummaryError ?? null}
      historySummaryEligible={plant.historySummaryEligible !== false}
      hasActiveJobs={plant.hasActiveJobs}
      onRequestSummary={() => historySummaryMutation.mutate()}
      summaryPending={historySummaryMutation.isPending}
      summaryMutationError={
        historySummaryMutation.error instanceof Error
          ? historySummaryMutation.error.message
          : null
      }
      readOnly={readOnly}
      reminderState={plant.reminderState}
      healthDiagnosis={analysis?.healthDiagnosis ?? null}
    />
  );

  const auxPanelBlock = readOnly ? (
    <SecondaryImagePanel image={screensaverAuxImage} alt={displayName} />
  ) : (
    <CarePanel
      analysis={analysis}
      waterRefreshing={waterSection.isRefreshing}
      fertilizerRefreshing={fertilizerSection.isRefreshing}
      pruningRefreshing={pruningSection.isRefreshing}
      lightRefreshing={lightSection.isRefreshing}
      placementRefreshing={placementSection.isRefreshing}
      healthRefreshing={healthSection.isRefreshing}
      reminderState={plant.reminderState}
      placementSeed={plant.location ?? null}
      placementSavePending={placementMutation.isPending}
      onEditPlacement={openPlacementDialog}
      commitWatering={(iso) => waterMutation.mutateAsync(iso)}
      onWaterDialogOpen={() => waterMutation.reset()}
      onFertilize={() => fertMutation.mutate()}
      onPrune={() => pruneMutation.mutate()}
      waterPending={waterMutation.isPending}
      fertPending={fertMutation.isPending}
      prunePending={pruneMutation.isPending}
      careActionError={
        [waterMutation.error, fertMutation.error, pruneMutation.error].find(
          (e): e is Error => e instanceof Error
        )?.message ?? null
      }
      onArchive={() => {
        if (confirm("Archive this plant? You can restore it later.")) {
          archiveMutation.mutate();
        }
      }}
      archivePending={archiveMutation.isPending}
      onRefresh={() => reanalysisMutation.mutate()}
      refreshPending={reanalysisMutation.isPending}
      onAddNote={(text) => addNoteMutation.mutate(text)}
      onAddImage={(image, noteText) => addImageMutation.mutate({ image, noteText })}
      addNotePending={addNoteMutation.isPending}
      addImagePending={addImageMutation.isPending}
      journalNoteError={
        addNoteMutation.error instanceof Error ? addNoteMutation.error.message : null
      }
      journalImageError={
        addImageMutation.error instanceof Error ? addImageMutation.error.message : null
      }
      readOnly={readOnly}
    />
  );

  return (
    <div
      className={`flex flex-1 gap-4 min-h-0 ${
        readOnly ? "py-8" : "pt-5"
      }`}
    >

      {/* ── Left column: hero + thumbs ─────────────────────────── */}
      <div className="flex flex-col gap-2 w-[34%] min-h-0">

        {/* Hero image — dominates the left column height */}
        <div className="relative flex-[4] min-h-0 rounded-2xl overflow-hidden bg-stone-100 shadow-sm">
          {mainImage ? (
            // Native img so LCP gets a real loading="eager" on the DOM node (Next/Image can still warn for dynamic remote URLs in dev).
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={plantImageSrc(mainImage.url)}
              alt={displayName}
              className="absolute inset-0 h-full w-full object-cover"
              loading="eager"
              fetchPriority="high"
              decoding="async"
            />
          ) : (
            <div className="flex h-full items-center justify-center text-stone-300 text-sm italic">
              Photo pending
            </div>
          )}
        </div>

        {/* Reference + Photo history thumbnails */}
        {!readOnly && (
        <div className="flex gap-4 items-start flex-shrink-0">

          {/* Reference images */}
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-stone-400 mb-1.5">
              Reference
            </p>
            <div className="flex gap-1.5">
              {referenceImages.length > 0 ? (
                referenceImages.slice(0, 3).map((img) => (
                  <ReferenceThumb key={img.id} img={img} />
                ))
              ) : (
                Array.from({ length: 3 }).map((_, i) => (
                  <div
                    key={i}
                    className="w-16 h-16 rounded-xl bg-stone-100 border border-stone-200 border-dashed"
                  />
                ))
              )}
            </div>
          </div>

          {/* Photo history with prev/next arrows */}
          <div className="flex-1 min-w-0">
            <p className="text-xs font-medium uppercase tracking-wide text-stone-400 mb-1.5">
              Photo history
            </p>
            <div className="flex items-center gap-1">
              {!readOnly && (
                <button
                  onClick={() => setHistoryIndex((i) => Math.max(0, i - 1))}
                  disabled={historyIndex === 0}
                  className="p-0.5 rounded-full text-stone-400 hover:text-stone-600 disabled:opacity-20 flex-shrink-0"
                >
                  <ChevronLeft size={16} />
                </button>
              )}
              <div className="flex gap-1.5 overflow-hidden">
                {historyImages.length > 0 ? (
                  historyImages.slice(historyIndex, historyIndex + HISTORY_PAGE).map((img) => (
                    <ThumbImage
                      key={img.id}
                      img={img}
                      sameAsHero={
                        mainImage != null &&
                        plantImageSrc(img.url) === plantImageSrc(mainImage.url)
                      }
                    />
                  ))
                ) : (
                  Array.from({ length: 4 }).map((_, i) => (
                    <div
                      key={i}
                      className="w-16 h-16 rounded-xl bg-stone-100 border border-stone-200 border-dashed flex-shrink-0"
                    />
                  ))
                )}
              </div>
              {!readOnly && (
                <button
                  onClick={() => setHistoryIndex((i) => Math.min(maxIndex, i + 1))}
                  disabled={historyIndex >= maxIndex}
                  className="p-0.5 rounded-full text-stone-400 hover:text-stone-600 disabled:opacity-20 flex-shrink-0"
                >
                  <ChevronRight size={16} />
                </button>
              )}
            </div>
          </div>
        </div>
        )}
      </div>

      {/* ── Right side ─────────────────────────────────────────────
           Screensaver: a 2-column grid so the second image can span the
             full height and visually match the hero column.
           Editable: the original flex-col stack (header + status sit
             above the full-width species/care grid). */}
      {readOnly ? (
        <div className="grid grid-cols-[3fr_2fr] grid-rows-[auto_auto_minmax(0,1fr)] gap-x-3 gap-y-2 flex-1 min-h-0">
          <div className="col-start-1 row-start-1">{headerBlock}</div>
          <div className="col-start-1 row-start-2">{statusBlock}</div>
          <div className="col-start-1 row-start-3 min-h-0">{speciesPanelBlock}</div>
          <div className="col-start-2 row-start-1 row-span-3 min-h-0">{auxPanelBlock}</div>
        </div>
      ) : (
        <div className="flex flex-col flex-1 gap-2 min-h-0">
          {headerBlock}
          {placementDialog}
          {statusBlock}
          <div className="grid grid-cols-[3fr_2fr] gap-3 flex-1 min-h-0">
            {speciesPanelBlock}
            {auxPanelBlock}
          </div>
        </div>
      )}
    </div>
  );
}

// ── Sub-components ───────────────────────────────────────────────────────────

function toDatetimeLocalValue(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function ThumbImage({ img, sameAsHero }: { img: PlantImageDto; sameAsHero?: boolean }) {
  return (
    <div className="relative w-16 h-16 rounded-xl overflow-hidden bg-stone-100 flex-shrink-0">
      <Image
        src={plantImageSrc(img.url)}
        alt=""
        fill
        className="object-cover"
        sizes="64px"
        loading={sameAsHero ? "eager" : "lazy"}
        priority={sameAsHero}
      />
    </div>
  );
}

function SecondaryImagePanel({
  image,
  alt,
}: {
  image: PlantImageDto | null;
  alt: string;
}) {
  return (
    <div className="relative rounded-2xl overflow-hidden bg-stone-100 shadow-sm h-full">
      {image ? (
        // Native img to match the hero's eager/high-priority treatment and avoid
        // Next/Image remote-URL warnings on the same dynamic sources.
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={plantImageSrc(image.url)}
          alt={alt}
          className="absolute inset-0 h-full w-full object-cover"
          loading="eager"
          fetchPriority="high"
          decoding="async"
        />
      ) : (
        <div className="flex h-full items-center justify-center text-stone-300 text-sm italic">
          Photo pending
        </div>
      )}
    </div>
  );
}

function ReferenceThumb({ img }: { img: PlantImageDto }) {
  const isPageLink = img.mimeType === "text/html";

  if (isPageLink) {
    const label = img.url.includes("inaturalist") ? "iNaturalist" : img.url.includes("gbif") ? "GBIF" : "Reference";
    return (
      <a
        href={img.url}
        target="_blank"
        rel="noopener noreferrer"
        title={`View on ${label}`}
        className="w-16 h-16 rounded-xl bg-stone-100 border border-stone-200 hover:border-stone-400 flex-shrink-0 flex flex-col items-center justify-center gap-1 transition-colors group"
      >
        <Globe size={18} className="text-stone-300 group-hover:text-stone-500 transition-colors" />
        <span className="text-[9px] font-medium text-stone-400 group-hover:text-stone-600 text-center leading-tight">
          {label}
        </span>
      </a>
    );
  }

  return (
    <div className="relative w-16 h-16 rounded-xl overflow-hidden bg-stone-100 flex-shrink-0">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={plantImageSrc(img.url)} alt="" className="w-full h-full object-cover" />
    </div>
  );
}

function SpeciesPanel({
  analysis,
  speciesIdRefreshing = false,
  speciesDescriptionRefreshing = false,
  historyEntries,
  heroImageUrl,
  historyDailyDigests,
  historySummaryText,
  historySummaryCompletedAt,
  historySummaryError,
  historySummaryEligible,
  hasActiveJobs,
  onRequestSummary,
  summaryPending,
  summaryMutationError,
  readOnly = false,
  reminderState = null,
  healthDiagnosis = null,
}: {
  analysis: PlantDetailResponse["latestAnalysis"];
  speciesIdRefreshing?: boolean;
  speciesDescriptionRefreshing?: boolean;
  historyEntries: PlantHistoryEntryDto[];
  heroImageUrl: string | null;
  historyDailyDigests: PlantDetailResponse["historyDailyDigests"];
  historySummaryText: string | null;
  historySummaryCompletedAt: string | null;
  historySummaryError: string | null;
  historySummaryEligible: boolean;
  hasActiveJobs: boolean;
  onRequestSummary: () => void;
  summaryPending: boolean;
  summaryMutationError: string | null;
  readOnly?: boolean;
  /** Screensaver-only: rendered as a compact icon row at the top-center. */
  reminderState?: PlantDetailResponse["reminderState"] | null;
  /** Screensaver-only: rendered as an amber health block beneath the history tiles. */
  healthDiagnosis?: string | null;
}) {
  // A plant is "species-ready" as soon as the species id section has produced
  // a name. We don't require every care section to have completed.
  const hasSpeciesIdentity =
    Boolean(analysis?.scientificName?.trim()) ||
    Boolean(analysis?.className?.trim());
  const speciesReady = hasSpeciesIdentity || analysis?.status === "COMPLETED";
  const digestTiles = historyDailyDigests ?? [];
  const hasDigestTiles = digestTiles.length > 0;
  const hasSummaryText =
    Boolean(historySummaryText?.trim()) || hasDigestTiles;
  const displayError = readOnly ? null : (historySummaryError ?? summaryMutationError);

  const historyDayTiles = useMemo(
    () =>
      buildHistoryDayTiles(
        historyEntries,
        historySummaryText,
        historySummaryCompletedAt
      ),
    [historyEntries, historySummaryText, historySummaryCompletedAt]
  );

  return (
    <div className="rounded-2xl border border-stone-200 bg-white p-3 h-full flex flex-col gap-3 min-h-0 overflow-hidden">
      {readOnly && reminderState && (
        <div className="flex justify-center flex-shrink-0">
          <ReminderIconRow reminderState={reminderState} size={20} />
        </div>
      )}
      <div className="flex-[0_1_auto] min-h-0 overflow-y-auto space-y-3">
        {!speciesReady || !analysis ? (
          <p className="text-sm text-stone-400 italic">
            {speciesIdRefreshing
              ? "Identifying species…"
              : "Species profile is being prepared…"}
          </p>
        ) : (
          <>
            {((analysis.scientificName || analysis.className) ||
              (analysis.nativeRegions && analysis.nativeRegions.length > 0)) && (
              <div
                className={`flex items-start gap-4 ${
                  analysis.nativeRegions && analysis.nativeRegions.length > 0
                    ? analysis.scientificName || analysis.className
                      ? "justify-between"
                      : "justify-end"
                    : ""
                }`}
              >
                {(analysis.scientificName || analysis.className) && (
                  <div className="text-sm text-stone-600 min-w-0 flex-1">
                    {analysis.scientificName && (
                      <p className="text-lg italic">{analysis.scientificName}</p>
                    )}
                    {analysis.className && (
                      <p className="text-stone-400 text-xs mt-0.5">{analysis.className}</p>
                    )}
                  </div>
                )}
                {analysis.nativeRegions && analysis.nativeRegions.length > 0 && (
                  <NativeToAside value={analysis.nativeRegions.join(", ")} />
                )}
              </div>
            )}

            {analysis.speciesOverview && analysis.speciesOverview.trim().length > 0 ? (
              <div>
                <div className="flex items-center gap-1.5 mb-2">
                  <ScrollText size={12} className="text-stone-400" />
                  <span className="text-xs font-medium uppercase tracking-wide text-stone-400">
                    Species overview
                  </span>
                  {speciesDescriptionRefreshing && (
                    <span className="text-[10px] text-stone-400 italic">Updating…</span>
                  )}
                </div>
                <SpeciesOverviewProse text={analysis.speciesOverview} />
              </div>
            ) : speciesDescriptionRefreshing ? (
              <div>
                <div className="flex items-center gap-1.5 mb-2">
                  <ScrollText size={12} className="text-stone-400" />
                  <span className="text-xs font-medium uppercase tracking-wide text-stone-400">
                    Species overview
                  </span>
                </div>
                <p className="text-sm text-stone-400 italic">Writing a short species overview…</p>
              </div>
            ) : null}

            {analysis.uses && analysis.uses.length > 0 && (
              <div>
                <div className="flex items-center gap-1.5 mb-2">
                  <Utensils size={12} className="text-stone-400" />
                  <span className="text-xs font-medium uppercase tracking-wide text-stone-400">Uses</span>
                </div>
                <ul className="space-y-1">
                  {analysis.uses.map((use, i) => (
                    <li key={i} className="text-sm text-stone-600 flex gap-2">
                      <span className="text-stone-300 flex-shrink-0">–</span>
                      <span>{use}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </>
        )}
      </div>

      <div className="flex flex-1 flex-col min-h-0 gap-2 border-t border-stone-100 pt-3 mt-1">
        <div className="flex items-center justify-between gap-2 flex-wrap flex-shrink-0">
          <div className="flex items-center gap-1.5">
            <Clock size={12} className="text-stone-400" />
            <span className="text-xs font-medium uppercase tracking-wide text-stone-400">History</span>
            {hasActiveJobs && (
              <span className="text-xs text-stone-400 italic">Updating…</span>
            )}
          </div>
          {!readOnly && (
            <button
              type="button"
              onClick={onRequestSummary}
              disabled={summaryPending || hasActiveJobs || !historySummaryEligible}
              className="text-xs font-medium text-stone-500 hover:text-stone-800 disabled:opacity-40 disabled:pointer-events-none"
            >
              {hasSummaryText ? "Refresh summary" : "Generate summary"}
            </button>
          )}
        </div>
        {displayError && (
          <p className="text-sm text-red-600 leading-snug flex-shrink-0" role="alert">
            {displayError}
          </p>
        )}
        {hasDigestTiles ? (
          <div className="flex-1 min-h-0 overflow-y-auto space-y-2 pr-0.5">
            {digestTiles.map((d) => (
              <div
                key={d.day}
                className="rounded-xl border border-stone-200 bg-stone-50/40 p-3 space-y-1"
              >
                <p className="text-[11px] font-semibold text-stone-500">
                  {formatDayTileTitle(d.day)}
                </p>
                <p className="text-sm text-stone-600 leading-relaxed whitespace-pre-wrap">
                  {d.digest}
                </p>
              </div>
            ))}
          </div>
        ) : historyDayTiles.length > 0 ? (
          <div className="flex-1 min-h-0 overflow-y-auto space-y-2 pr-0.5">
            {historyDayTiles.map((tile) => (
              <div
                key={tile.dayKey}
                className="rounded-xl border border-stone-200 bg-stone-50/40 p-3 space-y-1"
              >
                <p className="text-[11px] font-semibold text-stone-500">
                  {formatDayTileTitle(tile.dayKey)}
                </p>
                <div className="divide-y divide-stone-100/80">
                  {tile.rows.map((row, i) => (
                    <HistoryTimelineRow
                      key={rowKeyForTimelineRow(row, i)}
                      row={row}
                      heroImageUrl={heroImageUrl}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        ) : (
          !displayError && (
            <div className="flex-1 min-h-0 flex flex-col">
              <p className="text-sm text-stone-400 italic">
                {readOnly
                  ? "No history recorded yet."
                  : historySummaryEligible
                    ? "Tap Generate summary to build a short timeline from your journal, photos, and care log."
                    : "Add a journal note or record watering, fertilizer, or pruning before you can generate a history summary."}
              </p>
            </div>
          )
        )}
        {hasSummaryText && historySummaryCompletedAt && (
          <p className="text-[10px] text-stone-400 pt-0.5 flex-shrink-0">
            Updated {new Date(historySummaryCompletedAt).toLocaleString()}
          </p>
        )}
      </div>
      {readOnly && healthDiagnosis && (
        <div className="flex-shrink-0 border-t border-stone-100 pt-3">
          <div className="flex items-center gap-1.5 mb-2">
            <HeartPulse size={12} className="text-stone-400" />
            <span className="text-xs font-medium uppercase tracking-wide text-stone-400">
              Health
            </span>
          </div>
          <p className="text-sm text-stone-600 leading-relaxed whitespace-pre-wrap">
            {healthDiagnosis}
          </p>
        </div>
      )}
    </div>
  );
}

function CarePanel({
  analysis,
  waterRefreshing = false,
  fertilizerRefreshing = false,
  pruningRefreshing = false,
  lightRefreshing = false,
  placementRefreshing = false,
  healthRefreshing = false,
  reminderState,
  placementSeed,
  placementSavePending,
  onEditPlacement,
  commitWatering,
  onWaterDialogOpen,
  onFertilize,
  onPrune,
  waterPending,
  fertPending,
  prunePending,
  careActionError,
  onArchive,
  archivePending,
  onRefresh,
  refreshPending,
  onAddNote,
  onAddImage,
  addNotePending,
  addImagePending,
  journalNoteError,
  journalImageError,
  readOnly = false,
}: {
  analysis: PlantDetailResponse["latestAnalysis"];
  waterRefreshing?: boolean;
  fertilizerRefreshing?: boolean;
  pruningRefreshing?: boolean;
  lightRefreshing?: boolean;
  placementRefreshing?: boolean;
  healthRefreshing?: boolean;
  reminderState: PlantDetailResponse["reminderState"];
  placementSeed: string | null;
  placementSavePending: boolean;
  onEditPlacement: () => void;
  commitWatering: (wateredAtIso: string) => Promise<unknown>;
  onWaterDialogOpen: () => void;
  onFertilize: () => void;
  onPrune: () => void;
  waterPending: boolean;
  fertPending: boolean;
  prunePending: boolean;
  careActionError: string | null;
  onArchive: () => void;
  archivePending: boolean;
  onRefresh: () => void;
  refreshPending: boolean;
  onAddNote: (text: string) => void;
  onAddImage: (image: File, noteText?: string) => void;
  addNotePending: boolean;
  addImagePending: boolean;
  journalNoteError: string | null;
  journalImageError: string | null;
  readOnly?: boolean;
}) {
  const [waterDialogOpen, setWaterDialogOpen] = useState(false);
  const [wateredAtLocal, setWateredAtLocal] = useState("");
  const [expandedCareTopicId, setExpandedCareTopicId] = useState<string | null>(null);
  const ready = analysis?.status === "COMPLETED";
  const pg = analysis?.placementGuidance?.trim();
  const pgG = analysis?.placementGeneralGuidance?.trim();
  const showPlacement =
    Boolean(placementSeed) || (ready && Boolean((pg && pg.length > 0) || (pgG && pgG.length > 0)));

  const placementPrimary =
    ready && pg
      ? pg
      : ready && pgG
        ? pgG
        : "Placement guidance updates after analysis completes.";
  const placementDetail = ready && pg && pgG ? pgG : undefined;
  const placementValueMuted = !ready || (!pg && !pgG);

  function openWaterDialog() {
    onWaterDialogOpen();
    setWateredAtLocal(toDatetimeLocalValue(new Date()));
    setWaterDialogOpen(true);
  }

  async function submitWaterDialog() {
    const parsed = new Date(wateredAtLocal);
    if (Number.isNaN(parsed.getTime())) return;
    try {
      await commitWatering(parsed.toISOString());
      setWaterDialogOpen(false);
    } catch {
      /* error shown via careActionError */
    }
  }

  const placementRow = showPlacement ? (
    <CareTopicAccordion
      icon={<MapPin size={14} className="text-stone-400" />}
      label="Placement"
      value={placementPrimary}
      valueMuted={placementValueMuted}
      detail={placementDetail}
      actionNeeded={Boolean(reminderState?.placementAttentionNeeded)}
      isOpen={expandedCareTopicId === "placement"}
      onToggle={() =>
        setExpandedCareTopicId((cur) => (cur === "placement" ? null : "placement"))
      }
      refreshing={placementRefreshing}
      headerAccessory={
        readOnly ? undefined : (
          <button
            type="button"
            onClick={onEditPlacement}
            disabled={placementSavePending}
            className="p-0.5 rounded-full text-stone-300 hover:text-stone-500 hover:bg-stone-100 transition-colors opacity-0 group-hover:opacity-100 flex-shrink-0 disabled:pointer-events-none disabled:opacity-0"
            title="Edit placement notes"
          >
            <Pencil size={13} />
          </button>
        )
      }
    />
  ) : null;

  return (
    <div className="rounded-2xl border border-stone-200 bg-white p-3 h-full overflow-y-auto space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs font-medium uppercase tracking-wide text-stone-400">Care</p>
        <ReminderIconRow reminderState={reminderState} size={20} />
      </div>

      {!readOnly && (
        <Dialog
          open={waterDialogOpen}
          onOpenChange={(open) => {
            if (!waterPending) setWaterDialogOpen(open);
          }}
        >
          <DialogContent className="sm:max-w-md" showCloseButton={!waterPending}>
            <DialogHeader>
              <DialogTitle>Record watering</DialogTitle>
            </DialogHeader>
            <p className="text-xs text-stone-500 leading-snug">
              When did you water? This updates reminders and is included in care history for future analysis.
            </p>
            <div>
              <Label htmlFor="watered-at" className="text-stone-600 text-xs">
                Date and time
              </Label>
              <Input
                id="watered-at"
                type="datetime-local"
                value={wateredAtLocal}
                onChange={(e) => setWateredAtLocal(e.target.value)}
                className="mt-1.5"
                disabled={waterPending}
              />
            </div>
            {careActionError && (
              <p className="text-xs text-red-600 leading-snug" role="alert">
                {careActionError}
              </p>
            )}
            <div className="flex justify-end gap-2 pt-1">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setWaterDialogOpen(false)}
                disabled={waterPending}
              >
                Cancel
              </Button>
              <Button
                type="button"
                size="sm"
                onClick={() => void submitWaterDialog()}
                disabled={waterPending || !wateredAtLocal || Number.isNaN(new Date(wateredAtLocal).getTime())}
              >
                {waterPending ? "Saving…" : "Save"}
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      )}

      {!ready ? (
        <>
          {placementRow}
          <p className="text-sm text-stone-400 italic">Care profile is being prepared…</p>
        </>
      ) : (
        <div className="space-y-2">
          {(() => {
            const wateringInstruction = reminderState?.nextWateringInstruction ?? null;
            const wateringFallback = analysis?.wateringGuidance?.trim() || null;
            const wateringValue =
              wateringInstruction ?? wateringFallback ?? null;
            const showWatering =
              wateringValue != null ||
              Boolean(analysis?.wateringFrequency) ||
              Boolean(analysis?.wateringAmount);
            if (!showWatering) return null;
            return (
              <CareTopicAccordion
                icon={<Droplets size={14} className="text-sky-500" />}
                label="Watering"
                value={
                  wateringValue ?? "Watering guidance updates after analysis completes."
                }
                valueMuted={wateringInstruction == null}
                highlight={
                  wateringInstruction != null &&
                  Boolean(reminderState?.wateringDue || reminderState?.wateringOverdue)
                }
                actionNeeded={
                  wateringInstruction != null &&
                  Boolean(reminderState?.wateringDue || reminderState?.wateringOverdue)
                }
                isOpen={expandedCareTopicId === "watering"}
                onToggle={() =>
                  setExpandedCareTopicId((cur) => (cur === "watering" ? null : "watering"))
                }
                refreshing={waterRefreshing}
                detail={[
                  analysis?.wateringFrequency ? `Frequency: ${analysis.wateringFrequency}` : null,
                  wateringInstruction != null ? analysis?.wateringGuidance ?? null : null,
                  analysis?.wateringAmount ? `Amount: ${analysis.wateringAmount}` : null,
                ].filter(Boolean).join(" · ") || undefined}
              />
            );
          })()}
          {(() => {
            const fertInstruction = reminderState?.nextFertilizerInstruction ?? null;
            const fertFallback = analysis?.fertilizerGuidance?.trim() || null;
            const fertValue = fertInstruction ?? fertFallback ?? null;
            const showFertilizer =
              fertValue != null ||
              Boolean(analysis?.fertilizerFrequency) ||
              Boolean(analysis?.fertilizerType);
            if (!showFertilizer) return null;
            return (
              <CareTopicAccordion
                icon={<Leaf size={14} className="text-amber-600" />}
                label="Fertilizer"
                value={
                  fertValue ?? "Fertilizer guidance updates after analysis completes."
                }
                valueMuted={fertInstruction == null}
                highlight={fertInstruction != null && Boolean(reminderState?.fertilizerDue)}
                actionNeeded={
                  fertInstruction != null && Boolean(reminderState?.fertilizerDue)
                }
                isOpen={expandedCareTopicId === "fertilizer"}
                onToggle={() =>
                  setExpandedCareTopicId((cur) => (cur === "fertilizer" ? null : "fertilizer"))
                }
                refreshing={fertilizerRefreshing}
                detail={[
                  fertInstruction != null ? analysis?.fertilizerGuidance ?? null : null,
                  analysis?.fertilizerFrequency ? `Frequency: ${analysis.fertilizerFrequency}` : null,
                  analysis?.fertilizerType ? `Type: ${analysis.fertilizerType}` : null,
                ].filter(Boolean).join(" · ") || undefined}
              />
            );
          })()}
          {(() => {
            const pruningInstruction = reminderState?.nextPruningInstruction ?? null;
            const pruningFallback =
              analysis?.pruningActionSummary?.trim() ||
              analysis?.pruningGuidance?.trim() ||
              analysis?.pruningGeneralGuidance?.trim() ||
              null;
            const pruningValue = pruningInstruction ?? pruningFallback ?? null;
            if (pruningValue == null) return null;
            return (
              <CareTopicAccordion
                icon={<Scissors size={14} className="text-green-600" />}
                label="Pruning"
                value={pruningValue}
                valueMuted={pruningInstruction == null}
                highlight={pruningInstruction != null && Boolean(reminderState?.pruningDue)}
                actionNeeded={
                  pruningInstruction != null && Boolean(reminderState?.pruningDue)
                }
                isOpen={expandedCareTopicId === "pruning"}
                onToggle={() =>
                  setExpandedCareTopicId((cur) => (cur === "pruning" ? null : "pruning"))
                }
                refreshing={pruningRefreshing}
                detail={analysis?.pruningGeneralGuidance ?? undefined}
              />
            );
          })()}
          {reminderState?.weatherCareNote && (
            <div className="rounded-lg bg-sky-50 border border-sky-100 px-3 py-2 text-xs text-sky-900 leading-snug">
              <span className="font-medium text-sky-800">Weather: </span>
              {reminderState.weatherCareNote}
            </div>
          )}
          {analysis?.lightNeeds && (
            <CareTopicAccordion
              icon={<Sun size={14} className="text-amber-400" />}
              label="Light"
              value={analysis.lightNeeds}
              actionNeeded={Boolean(reminderState?.lightAttentionNeeded)}
              isOpen={expandedCareTopicId === "light"}
              onToggle={() =>
                setExpandedCareTopicId((cur) => (cur === "light" ? null : "light"))
              }
              refreshing={lightRefreshing}
              detail={analysis?.lightGeneralGuidance ?? undefined}
            />
          )}
          {placementRow}
          {analysis?.healthDiagnosis ? (
            <div className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 border border-amber-100">
              <span className="font-medium">Health: </span>
              {analysis.healthDiagnosis}
              {healthRefreshing && (
                <span className="ml-1.5 text-[10px] text-amber-600/70 italic">Updating…</span>
              )}
            </div>
          ) : healthRefreshing ? (
            <div className="rounded-lg bg-amber-50/40 px-3 py-2 text-xs text-amber-700/60 border border-amber-100/60 italic">
              Checking health…
            </div>
          ) : null}
          {analysis?.goalSuggestions && (
            <div className="rounded-lg bg-green-50 px-3 py-2 text-xs text-green-700 border border-green-100">
              <span className="font-medium">Goals: </span>
              {analysis.goalSuggestions}
            </div>
          )}
        </div>
      )}

      {!readOnly && (
        <>
          {/* Action buttons */}
          <div className="pt-1.5 border-t border-stone-100 space-y-1.5">
            <div className="grid grid-cols-3 gap-2">
              <Button
                variant="outline" size="sm"
                onClick={openWaterDialog} disabled={waterPending}
                className="text-sky-600 border-sky-200 hover:bg-sky-50 text-xs"
              >
                <Droplets size={13} className="mr-1" />
                {waterPending ? "…" : "Watered"}
              </Button>
              <Button
                variant="outline" size="sm"
                onClick={onFertilize} disabled={fertPending}
                className="text-amber-600 border-amber-200 hover:bg-amber-50 text-xs"
              >
                <Leaf size={13} className="mr-1" />
                {fertPending ? "…" : "Fertilized"}
              </Button>
              <Button
                variant="outline" size="sm"
                onClick={onPrune} disabled={prunePending}
                className="text-green-700 border-green-200 hover:bg-green-50 text-xs"
              >
                <Scissors size={13} className="mr-1" />
                {prunePending ? "…" : "Pruned"}
              </Button>
            </div>
            {careActionError && (
              <p className="text-xs text-red-600 leading-snug" role="alert">
                {careActionError}
              </p>
            )}
            <Button
              variant="ghost" size="sm"
              onClick={onRefresh} disabled={refreshPending}
              className="w-full text-stone-500 hover:text-stone-700 text-xs"
            >
              <RefreshCw size={13} className={`mr-1 ${refreshPending ? "animate-spin" : ""}`} />
              {refreshPending ? "Refreshing…" : "Refresh data"}
            </Button>
            <Button
              variant="ghost" size="sm"
              onClick={onArchive} disabled={archivePending}
              className="w-full text-stone-400 hover:text-stone-600 text-xs"
            >
              <Archive size={13} className="mr-1" />
              Archive
            </Button>
          </div>

          <div className="pt-2 border-t border-stone-100">
            <CareObservationInput
              onAddNote={onAddNote}
              onAddImage={onAddImage}
              addNotePending={addNotePending}
              addImagePending={addImagePending}
              noteError={journalNoteError}
              imageError={journalImageError}
            />
          </div>
        </>
      )}
    </div>
  );
}

/** Right-aligned native range, paired on the same row as the species name in the species pane. */
function NativeToAside({ value }: { value: string }) {
  return (
    <div className="flex flex-col items-end gap-0.5 shrink-0 max-w-[min(50%,14rem)] text-right">
      <div className="flex items-center gap-1.5 justify-end">
        <Globe size={14} className="text-stone-400 shrink-0" />
        <span className="text-xs font-medium uppercase tracking-wide text-stone-400">Native to</span>
      </div>
      <p className="text-sm text-stone-600 leading-snug">{value}</p>
    </div>
  );
}

function rowKeyForTimelineRow(row: TimelineRow, index: number): string {
  if (row.kind === "entry") {
    return `e-${row.entry.id}-${row.entry.entryKind ?? "x"}-${index}`;
  }
  return `s-${row.at.getTime()}-${index}`;
}

function HistoryTimelineRow({
  row,
  heroImageUrl,
}: {
  row: TimelineRow;
  heroImageUrl: string | null;
}) {
  if (row.kind === "entry") {
    return (
      <HistoryEntryRow
        entry={row.entry}
        sameAsHero={
          heroImageUrl != null &&
          row.entry.image != null &&
          plantImageSrc(row.entry.image.url) === heroImageUrl
        }
        omitBorder
      />
    );
  }
  return <HistorySummaryTextRow text={row.text} at={row.at} />;
}

function HistorySummaryTextRow({ text, at }: { text: string; at: Date }) {
  const showTime = at.getFullYear() >= 2000;
  const timeStr = showTime
    ? at.toLocaleString(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: "numeric",
        minute: "2-digit",
      })
    : null;

  return (
    <div className="flex gap-3 items-start py-2">
      <div className="flex-1 min-w-0">
        <p className="text-sm text-stone-600 leading-relaxed whitespace-pre-wrap">{text}</p>
        {timeStr && (
          <div className="flex items-center gap-1 mt-1">
            <Clock size={10} className="text-stone-300 flex-shrink-0" />
            <span className="text-xs text-stone-400">{timeStr}</span>
          </div>
        )}
      </div>
    </div>
  );
}

function HistoryEntryRow({
  entry,
  sameAsHero,
  omitBorder,
}: {
  entry: PlantHistoryEntryDto;
  sameAsHero?: boolean;
  omitBorder?: boolean;
}) {
  const date = new Date(entry.createdAt).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });

  return (
    <div
      className={
        omitBorder
          ? "flex gap-3 items-start py-2"
          : "flex gap-3 items-start py-2 border-b border-stone-100 last:border-0"
      }
    >
      {entry.image ? (
        <div className="relative w-14 h-14 rounded-lg overflow-hidden bg-stone-100 flex-shrink-0">
          <Image
            src={plantImageSrc(entry.image.url)}
            alt=""
            fill
            className="object-cover"
            sizes="56px"
            loading={sameAsHero ? "eager" : "lazy"}
            priority={sameAsHero}
          />
        </div>
      ) : null}
      <div className="flex-1 min-w-0">
        {entry.noteText && (
          <p className="text-sm text-stone-700 leading-snug">{entry.noteText}</p>
        )}
        <div className="flex items-center gap-1 mt-1">
          <Clock size={10} className="text-stone-300 flex-shrink-0" />
          <span className="text-xs text-stone-400">{date}</span>
        </div>
      </div>
    </div>
  );
}
