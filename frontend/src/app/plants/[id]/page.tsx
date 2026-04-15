"use client";

import { useState, useRef, useEffect, useMemo } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft, Droplets, Leaf, Scissors, Archive,
  ChevronLeft, ChevronRight, Globe, ScrollText, Sun, MapPin, Utensils, Pencil, Check, X, RefreshCw,
  Camera, Send, Clock,
} from "lucide-react";
import Link from "next/link";
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
import { Skeleton } from "@/components/ui/skeleton";
import { PlantStatusCard } from "@/components/plant/PlantStatusCard";
import { ReminderIconRow } from "@/components/plant/ReminderIconRow";
import {
  getPlant,
  archivePlant,
  recordWatering,
  recordFertilizer,
  recordPrune,
  updatePlantName,
  updatePlantGrowing,
  updatePlantPlacement,
  requestReanalysis,
  addHistoryNote,
  addHistoryImage,
  requestHistorySummary,
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

const INDOOR_PLACEMENT_SNIPPET_MAX = 56;

/** First line of placement notes, trimmed — used beside "Indoor" in the header. */
function summarizeIndoorPlacementLabel(location: string | null | undefined): string | null {
  const raw = location?.trim();
  if (!raw) return null;
  const firstLine = raw.split(/\r\n|\n|\r/)[0].trim();
  if (!firstLine) return null;
  const withoutIndoorEcho = stripRedundantIndoorPrefix(firstLine);
  const cleaned = withoutIndoorEcho.replace(/\s+/g, " ").trim();
  if (!cleaned) return null;
  if (cleaned.length <= INDOOR_PLACEMENT_SNIPPET_MAX) return cleaned;
  return `${cleaned.slice(0, INDOOR_PLACEMENT_SNIPPET_MAX - 1).trimEnd()}…`;
}

function stripRedundantIndoorPrefix(text: string): string {
  const t = text.trim();
  const stripped = t.replace(/^(indoor|inside)\s*[:,]?\s*/i, "").trim();
  return stripped.length > 0 ? stripped : t;
}

export default function PlantDetailPage() {
  const { id } = useParams<{ id: string }>();
  const plantId = Number(id);
  const router = useRouter();
  const queryClient = useQueryClient();

  const [historyIndex, setHistoryIndex] = useState(0);
  const [editingName, setEditingName] = useState(false);
  const [nameInput, setNameInput] = useState("");
  const [editingGrowing, setEditingGrowing] = useState(false);
  const [growingCtx, setGrowingCtx] = useState<PlantGrowingContext>("INDOOR");
  const [latInput, setLatInput] = useState("");
  const [lonInput, setLonInput] = useState("");

  const { data: plant, isLoading } = useQuery({
    queryKey: ["plant", plantId],
    queryFn: () => getPlant(plantId),
    refetchInterval: (query) => {
      // Stale cache can still have hasActiveJobs true while refetches fail; don't poll on hard errors.
      if (query.state.status === "error") {
        return false;
      }
      const data = query.state.data;
      if (!data) return 3000;
      return data.hasActiveJobs ? 3000 : false;
    },
  });

  const archiveMutation = useMutation({
    mutationFn: () => archivePlant(plantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      router.push("/plants");
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
    mutationFn: (body: {
      growingContext: PlantGrowingContext;
      latitude: number | null;
      longitude: number | null;
    }) => updatePlantGrowing(plantId, body),
    onSuccess: () => {
      setEditingGrowing(false);
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
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plant", plantId] }),
  });

  const addNoteMutation = useMutation({
    mutationFn: (noteText: string) => addHistoryNote(plantId, noteText),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      addNoteMutation.reset();
    },
  });

  const addImageMutation = useMutation({
    mutationFn: ({ image, noteText }: { image: File; noteText?: string }) =>
      addHistoryImage(plantId, image, noteText),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
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
    setGrowingCtx(plant?.growingContext ?? "INDOOR");
    setLatInput(plant?.latitude != null ? String(plant.latitude) : "");
    setLonInput(plant?.longitude != null ? String(plant.longitude) : "");
    setEditingGrowing(true);
  }

  function saveGrowing() {
    const latTrim = latInput.trim();
    const lonTrim = lonInput.trim();
    const lat = latTrim === "" ? null : Number.parseFloat(latTrim);
    const lon = lonTrim === "" ? null : Number.parseFloat(lonTrim);
    if (latTrim !== "" && !Number.isFinite(lat)) {
      window.alert("Latitude must be a number.");
      return;
    }
    if (lonTrim !== "" && !Number.isFinite(lon)) {
      window.alert("Longitude must be a number.");
      return;
    }
    growingMutation.mutate({
      growingContext: growingCtx,
      latitude: latTrim === "" ? null : lat,
      longitude: lonTrim === "" ? null : lon,
    });
  }

  function cancelEditGrowing() {
    setEditingGrowing(false);
  }

  if (isLoading) return <LoadingSkeleton />;
  if (!plant) return null;

  const analysis = plant.latestAnalysis;
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
  const indoorPlacementSnippet =
    (plant.growingContext ?? "INDOOR") === "INDOOR"
      ? summarizeIndoorPlacementLabel(plant.location)
      : null;

  return (
    <main className="h-[100dvh] overflow-hidden flex flex-col relative px-3 py-2">
      {/* Back nav — absolute so it doesn't push content down */}
      <Link
        href="/plants"
        className="absolute top-2 left-3 z-10 inline-flex items-center gap-1 text-xs text-stone-400 hover:text-stone-600"
      >
        <ArrowLeft size={12} /> All plants
      </Link>

      {/* ── Two-column landscape layout ───────────────────────── */}
      <div className="flex flex-1 gap-4 min-h-0 pt-5">

        {/* ── Left column: hero + thumbs ─────────────────────────── */}
        <div className="flex flex-col gap-2 w-[34%] min-h-0">

          {/* Hero image — dominates the left column height */}
          <div className="relative flex-[4] min-h-0 rounded-2xl overflow-hidden bg-stone-100 shadow-sm">
            {mainImage ? (
              // Native img so LCP gets a real loading="eager" on the DOM node (Next/Image can still warn for dynamic remote URLs in dev).
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={mainImage.url}
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
                <button
                  onClick={() => setHistoryIndex((i) => Math.max(0, i - 1))}
                  disabled={historyIndex === 0}
                  className="p-0.5 rounded-full text-stone-400 hover:text-stone-600 disabled:opacity-20 flex-shrink-0"
                >
                  <ChevronLeft size={16} />
                </button>
                <div className="flex gap-1.5 overflow-hidden">
                  {historyImages.length > 0 ? (
                    historyImages.slice(historyIndex, historyIndex + HISTORY_PAGE).map((img) => (
                      <ThumbImage
                        key={img.id}
                        img={img}
                        sameAsHero={mainImage != null && img.url === mainImage.url}
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
                <button
                  onClick={() => setHistoryIndex((i) => Math.min(maxIndex, i + 1))}
                  disabled={historyIndex >= maxIndex}
                  className="p-0.5 rounded-full text-stone-400 hover:text-stone-600 disabled:opacity-20 flex-shrink-0"
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* ── Right column: header + status + panels ────────────── */}
        <div className="flex flex-col flex-1 gap-2 min-h-0">

          {/* Name / species header */}
          <div className="flex items-start justify-between gap-4">
            {/* Left: name + species */}
            <div className="min-w-0">
              {editingName ? (
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
                  <button
                    onClick={startEditName}
                    className="p-1 rounded-full text-stone-300 hover:text-stone-500 hover:bg-stone-100 transition-colors opacity-0 group-hover:opacity-100 flex-shrink-0"
                    title="Edit name"
                  >
                    <Pencil size={13} />
                  </button>
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
                <button
                  type="button"
                  onClick={startEditGrowing}
                  className="p-0.5 rounded-full text-stone-300 hover:text-stone-500 hover:bg-stone-100 transition-colors opacity-0 group-hover:opacity-100 flex-shrink-0"
                  title="Edit growing environment and coordinates"
                >
                  <Pencil size={13} />
                </button>
                <div className="min-w-0 text-xs text-stone-500 leading-snug">
                  <p>
                    {(plant.growingContext ?? "INDOOR") === "OUTDOOR" ? "Outdoor" : "Indoor"}
                    {indoorPlacementSnippet ? ` · ${indoorPlacementSnippet}` : ""}
                    {plant.latitude != null && plant.longitude != null
                      ? " · Coords on file"
                      : (plant.growingContext ?? "INDOOR") === "OUTDOOR"
                        ? " · Add coords for weather reminders"
                        : ""}
                  </p>
                  {plant.reminderState?.weatherCareNote && (
                    <p className="mt-1 text-xs text-stone-500 leading-snug">{plant.reminderState.weatherCareNote}</p>
                  )}
                </div>
              </div>
            </div>
          </div>

          <Dialog
            open={editingGrowing}
            onOpenChange={(open) => {
              if (!open) cancelEditGrowing();
            }}
          >
            <DialogContent className="sm:max-w-md" showCloseButton>
              <DialogHeader>
                <DialogTitle>Growing &amp; local weather</DialogTitle>
              </DialogHeader>
              <div className="space-y-3">
                <label className="block text-xs text-stone-500">
                  Environment
                  <select
                    value={growingCtx}
                    onChange={(e) => setGrowingCtx(e.target.value as PlantGrowingContext)}
                    className="mt-1 w-full rounded-md border border-stone-200 bg-white px-2 py-1.5 text-sm text-stone-800"
                  >
                    <option value="INDOOR">Indoor</option>
                    <option value="OUTDOOR">Outdoor</option>
                  </select>
                </label>
                {growingCtx === "OUTDOOR" && (
                  <div className="grid grid-cols-2 gap-2">
                    <label className="text-xs text-stone-500">
                      Latitude
                      <input
                        type="text"
                        inputMode="decimal"
                        value={latInput}
                        onChange={(e) => setLatInput(e.target.value)}
                        className="mt-0.5 w-full rounded-md border border-stone-200 bg-white px-2 py-1 text-sm"
                        placeholder="e.g. 40.7"
                      />
                    </label>
                    <label className="text-xs text-stone-500">
                      Longitude
                      <input
                        type="text"
                        inputMode="decimal"
                        value={lonInput}
                        onChange={(e) => setLonInput(e.target.value)}
                        className="mt-0.5 w-full rounded-md border border-stone-200 bg-white px-2 py-1 text-sm"
                        placeholder="e.g. -74.0"
                      />
                    </label>
                  </div>
                )}
                <p className="text-[11px] text-stone-400 leading-snug">
                  Coordinates are stored to query public weather only (Open-Meteo). Use approximate values if you prefer.
                </p>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={cancelEditGrowing}
                  disabled={growingMutation.isPending}
                >
                  Cancel
                </Button>
                <Button
                  type="button"
                  size="sm"
                  onClick={saveGrowing}
                  disabled={growingMutation.isPending}
                >
                  Save
                </Button>
              </div>
            </DialogContent>
          </Dialog>

          {/* Job status */}
          <PlantStatusCard status={analysis?.status ?? null} />

          {/* Species + Care panels — fill remaining height, scroll internally */}
          <div className="grid grid-cols-[3fr_2fr] gap-3 flex-1 min-h-0">
            <SpeciesPanel
              analysis={analysis}
              historyEntries={plant.historyEntries ?? []}
              heroImageUrl={mainImage?.url ?? null}
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
            />
            <CarePanel
              analysis={analysis}
              reminderState={plant.reminderState}
              placementSeed={plant.location ?? null}
              savePlacement={(loc) => placementMutation.mutateAsync(loc)}
              placementSavePending={placementMutation.isPending}
              placementSaveError={
                placementMutation.error instanceof Error ? placementMutation.error.message : null
              }
              onPlacementDialogOpen={() => placementMutation.reset()}
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
            />
          </div>
        </div>
      </div>
    </main>
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
        src={img.url}
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
      <img src={img.url} alt="" className="w-full h-full object-cover" />
    </div>
  );
}

function SpeciesPanel({
  analysis,
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
}: {
  analysis: PlantDetailResponse["latestAnalysis"];
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
}) {
  const speciesReady = analysis?.status === "COMPLETED";
  const digestTiles = historyDailyDigests ?? [];
  const hasDigestTiles = digestTiles.length > 0;
  const hasSummaryText =
    Boolean(historySummaryText?.trim()) || hasDigestTiles;
  const displayError = historySummaryError ?? summaryMutationError;

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
      <div className="flex-[0_1_auto] min-h-0 overflow-y-auto space-y-3">
        {!speciesReady ? (
          <p className="text-sm text-stone-400 italic">Species profile is being prepared…</p>
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

            {analysis.speciesOverview && analysis.speciesOverview.trim().length > 0 && (
              <div>
                <div className="flex items-center gap-1.5 mb-2">
                  <ScrollText size={12} className="text-stone-400" />
                  <span className="text-xs font-medium uppercase tracking-wide text-stone-400">
                    Species overview
                  </span>
                </div>
                <SpeciesOverviewProse text={analysis.speciesOverview} />
              </div>
            )}

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
          <button
            type="button"
            onClick={onRequestSummary}
            disabled={summaryPending || hasActiveJobs || !historySummaryEligible}
            className="text-xs font-medium text-stone-500 hover:text-stone-800 disabled:opacity-40 disabled:pointer-events-none"
          >
            {hasSummaryText ? "Refresh summary" : "Generate summary"}
          </button>
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
                {historySummaryEligible
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
    </div>
  );
}

function CarePanel({
  analysis,
  reminderState,
  placementSeed,
  savePlacement,
  placementSavePending,
  placementSaveError,
  onPlacementDialogOpen,
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
}: {
  analysis: PlantDetailResponse["latestAnalysis"];
  reminderState: PlantDetailResponse["reminderState"];
  placementSeed: string | null;
  savePlacement: (location: string | null) => Promise<unknown>;
  placementSavePending: boolean;
  placementSaveError: string | null;
  onPlacementDialogOpen: () => void;
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
}) {
  const [placementDialogOpen, setPlacementDialogOpen] = useState(false);
  const [placementInput, setPlacementInput] = useState("");
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

  function openPlacementDialog() {
    onPlacementDialogOpen();
    setPlacementInput(placementSeed ?? "");
    setPlacementDialogOpen(true);
  }

  async function submitPlacementDialog() {
    try {
      const trimmed = placementInput.trim();
      await savePlacement(trimmed === "" ? null : trimmed);
      setPlacementDialogOpen(false);
    } catch {
      /* error shown via placementSaveError */
    }
  }

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
      actionNeeded={false}
      isOpen={expandedCareTopicId === "placement"}
      onToggle={() =>
        setExpandedCareTopicId((cur) => (cur === "placement" ? null : "placement"))
      }
      headerAccessory={
        <button
          type="button"
          onClick={openPlacementDialog}
          disabled={placementSavePending}
          className="p-0.5 rounded-full text-stone-300 hover:text-stone-500 hover:bg-stone-100 transition-colors opacity-0 group-hover:opacity-100 flex-shrink-0 disabled:pointer-events-none disabled:opacity-0"
          title="Edit placement notes"
        >
          <Pencil size={13} />
        </button>
      }
    />
  ) : null;

  return (
    <div className="rounded-2xl border border-stone-200 bg-white p-3 h-full overflow-y-auto space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs font-medium uppercase tracking-wide text-stone-400">Care</p>
        <ReminderIconRow reminderState={reminderState} size={20} />
      </div>

      <Dialog
        open={placementDialogOpen}
        onOpenChange={(open) => {
          if (!open && !placementSavePending) setPlacementDialogOpen(false);
        }}
      >
        <DialogContent className="sm:max-w-md" showCloseButton={!placementSavePending}>
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
              disabled={placementSavePending}
            />
          </label>
          <p className="text-[11px] text-stone-400 tabular-nums">{placementInput.length}/800</p>
          {placementSaveError && (
            <p className="text-xs text-red-600 leading-snug" role="alert">
              {placementSaveError}
            </p>
          )}
          <div className="flex justify-end gap-2 pt-1">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setPlacementDialogOpen(false)}
              disabled={placementSavePending}
            >
              Cancel
            </Button>
            <Button type="button" size="sm" onClick={() => void submitPlacementDialog()} disabled={placementSavePending}>
              {placementSavePending ? "Saving…" : "Save"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

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

      {!ready ? (
        <>
          {placementRow}
          <p className="text-sm text-stone-400 italic">Care profile is being prepared…</p>
        </>
      ) : (
        <div className="space-y-2">
          {reminderState?.nextWateringInstruction && (
            <CareTopicAccordion
              icon={<Droplets size={14} className="text-sky-500" />}
              label="Watering"
              value={reminderState.nextWateringInstruction}
              highlight={reminderState.wateringDue || reminderState.wateringOverdue}
              actionNeeded={
                Boolean(reminderState.wateringDue || reminderState.wateringOverdue)
              }
              isOpen={expandedCareTopicId === "watering"}
              onToggle={() =>
                setExpandedCareTopicId((cur) => (cur === "watering" ? null : "watering"))
              }
              detail={[
                analysis?.wateringFrequency ? `Frequency: ${analysis.wateringFrequency}` : null,
                analysis?.wateringGuidance ?? null,
              ].filter(Boolean).join(" · ") || undefined}
            />
          )}
          {reminderState?.nextFertilizerInstruction && (
            <CareTopicAccordion
              icon={<Leaf size={14} className="text-amber-600" />}
              label="Fertilizer"
              value={reminderState.nextFertilizerInstruction}
              highlight={reminderState.fertilizerDue}
              actionNeeded={Boolean(reminderState.fertilizerDue)}
              isOpen={expandedCareTopicId === "fertilizer"}
              onToggle={() =>
                setExpandedCareTopicId((cur) => (cur === "fertilizer" ? null : "fertilizer"))
              }
              detail={[
                analysis?.fertilizerGuidance,
                analysis?.fertilizerFrequency ? `Frequency: ${analysis.fertilizerFrequency}` : null,
              ].filter(Boolean).join(" · ") || undefined}
            />
          )}
          {reminderState?.nextPruningInstruction && (
            <CareTopicAccordion
              icon={<Scissors size={14} className="text-green-600" />}
              label="Pruning"
              value={reminderState.nextPruningInstruction}
              highlight={reminderState.pruningDue}
              actionNeeded={Boolean(reminderState.pruningDue)}
              isOpen={expandedCareTopicId === "pruning"}
              onToggle={() =>
                setExpandedCareTopicId((cur) => (cur === "pruning" ? null : "pruning"))
              }
              detail={analysis?.pruningGeneralGuidance ?? undefined}
            />
          )}
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
              actionNeeded={false}
              isOpen={expandedCareTopicId === "light"}
              onToggle={() =>
                setExpandedCareTopicId((cur) => (cur === "light" ? null : "light"))
              }
              detail={analysis?.lightGeneralGuidance ?? undefined}
            />
          )}
          {placementRow}
          {analysis?.healthDiagnosis && (
            <div className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700 border border-amber-100">
              <span className="font-medium">Health: </span>
              {analysis.healthDiagnosis}
            </div>
          )}
          {analysis?.goalSuggestions && (
            <div className="rounded-lg bg-green-50 px-3 py-2 text-xs text-green-700 border border-green-100">
              <span className="font-medium">Goals: </span>
              {analysis.goalSuggestions}
            </div>
          )}
        </div>
      )}

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
    </div>
  );
}

function CareObservationInput({
  onAddNote,
  onAddImage,
  addNotePending,
  addImagePending,
  noteError,
  imageError,
}: {
  onAddNote: (text: string) => void;
  onAddImage: (image: File, noteText?: string) => void;
  addNotePending: boolean;
  addImagePending: boolean;
  noteError: string | null;
  imageError: string | null;
}) {
  const [noteText, setNoteText] = useState("");
  const fileInputRef = useRef<HTMLInputElement>(null);
  const MAX_CHARS = 180;

  function submitNote() {
    const trimmed = noteText.trim();
    if (!trimmed) return;
    onAddNote(trimmed);
    setNoteText("");
  }

  function handleImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const caption = noteText.trim() || undefined;
    onAddImage(file, caption);
    setNoteText("");
    e.target.value = "";
  }

  const journalError = noteError ?? imageError;

  return (
    <div className="rounded-2xl border border-stone-200 bg-white p-3 flex flex-col gap-2">
        <textarea
          value={noteText}
          onChange={(e) => setNoteText(e.target.value.slice(0, MAX_CHARS))}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              submitNote();
            }
          }}
          placeholder="Add an observation about your plant…"
          rows={2}
          className="w-full resize-none text-sm text-stone-700 placeholder:text-stone-300 bg-transparent outline-none leading-snug"
        />
        {journalError && (
          <p className="text-xs text-red-600 leading-snug" role="alert">
            {journalError}
          </p>
        )}
        <div className="flex items-center justify-between">
          <span className={`text-xs tabular-nums ${noteText.length >= MAX_CHARS ? "text-red-400" : "text-stone-300"}`}>
            {noteText.length}/{MAX_CHARS}
          </span>
          <div className="flex items-center gap-2">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp,image/heic"
              className="hidden"
              onChange={handleImageChange}
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={addImagePending}
              className="p-1.5 rounded-full text-stone-400 hover:text-stone-600 hover:bg-stone-100 transition-colors disabled:opacity-40"
              title={noteText.trim() ? "Add photo with note as caption" : "Add photo"}
            >
              <Camera size={16} />
            </button>
            <button
              type="button"
              onClick={submitNote}
              disabled={!noteText.trim() || addNotePending}
              className="p-1.5 rounded-full text-green-600 hover:bg-green-50 transition-colors disabled:opacity-30"
              title="Save note"
            >
              <Send size={16} />
            </button>
          </div>
        </div>
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
          row.entry.image.url === heroImageUrl
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
            src={entry.image.url}
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

function LoadingSkeleton() {
  return (
    <main className="h-[100dvh] overflow-hidden flex flex-col px-3 py-2">
      <div className="flex flex-1 gap-4 min-h-0 pt-5">
        <div className="flex flex-col gap-2 w-[34%] min-h-0">
          <Skeleton className="flex-1 min-h-0 rounded-2xl" />
          <div className="flex gap-4">
            <div className="flex gap-1.5">
              {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="w-16 h-16 rounded-xl" />)}
            </div>
            <div className="flex gap-1.5">
              {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="w-16 h-16 rounded-xl" />)}
            </div>
          </div>
        </div>
        <div className="flex flex-col flex-1 gap-2 min-h-0">
          <Skeleton className="h-7 w-48" />
          <Skeleton className="h-4 w-32" />
          <div className="grid grid-cols-[3fr_2fr] gap-3 flex-1 min-h-0">
            <Skeleton className="rounded-2xl h-full" />
            <Skeleton className="rounded-2xl h-full" />
          </div>
        </div>
      </div>
    </main>
  );
}
