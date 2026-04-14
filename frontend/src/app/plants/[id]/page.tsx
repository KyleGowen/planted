"use client";

import { useState, useRef, useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft, Droplets, Leaf, Scissors, Archive,
  ChevronLeft, ChevronRight, Globe, ScrollText, Sun, MapPin, BookOpen, Utensils, Pencil, Check, X, RefreshCw,
  Camera, Send, Clock,
} from "lucide-react";
import Link from "next/link";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { ButtonLink } from "@/components/ui/button-link";
import { Skeleton } from "@/components/ui/skeleton";
import { PlantStatusCard } from "@/components/plant/PlantStatusCard";
import { ReminderIconRow } from "@/components/plant/ReminderIconRow";
import {
  getPlant,
  archivePlant,
  recordWatering,
  recordFertilizer,
  updatePlantName,
  requestReanalysis,
  addHistoryNote,
  addHistoryImage,
  requestHistorySummary,
} from "@/lib/api";
import type { PlantDetailResponse, PlantHistoryEntryDto, PlantImageDto } from "@/types/plant";
import { SpeciesOverviewProse } from "@/components/plant/SpeciesOverviewProse";

export default function PlantDetailPage() {
  const { id } = useParams<{ id: string }>();
  const plantId = Number(id);
  const router = useRouter();
  const queryClient = useQueryClient();

  const [historyIndex, setHistoryIndex] = useState(0);
  const [editingName, setEditingName] = useState(false);
  const [nameInput, setNameInput] = useState("");

  const { data: plant, isLoading } = useQuery({
    queryKey: ["plant", plantId],
    queryFn: () => getPlant(plantId),
    refetchInterval: (query) => {
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
    mutationFn: () => recordWatering(plantId, { wateredAt: new Date().toISOString() }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plant", plantId] }),
  });

  const fertMutation = useMutation({
    mutationFn: () => recordFertilizer(plantId, { fertilizedAt: new Date().toISOString() }),
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

  const reanalysisMutation = useMutation({
    mutationFn: () => requestReanalysis(plantId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plant", plantId] }),
  });

  const addNoteMutation = useMutation({
    mutationFn: (noteText: string) => addHistoryNote(plantId, noteText),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plant", plantId] }),
  });

  const addImageMutation = useMutation({
    mutationFn: ({ image, noteText }: { image: File; noteText?: string }) =>
      addHistoryImage(plantId, image, noteText),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plant", plantId] }),
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

        {/* ── Left column: hero + thumbs + history notes ────────── */}
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

          {/* History observations — natural height, pinned to bottom of left column */}
          <div className="mt-auto flex-shrink-0">
            <HistorySection
              entries={plant.historyEntries ?? []}
              heroImageUrl={mainImage?.url ?? null}
              onAddNote={(text) => addNoteMutation.mutate(text)}
              onAddImage={(image, noteText) => addImageMutation.mutate({ image, noteText })}
              addNotePending={addNoteMutation.isPending}
              addImagePending={addImageMutation.isPending}
            />
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

            {/* Right: placement description */}
            {(plant.location || plant.geoCity || plant.geoState || plant.geoCountry) && (
              <div className="flex flex-col items-end gap-0.5 flex-shrink-0 text-right">
                {plant.location && (
                  <p className="text-sm text-stone-400">{plant.location}</p>
                )}
                {(plant.geoCity || plant.geoState || plant.geoCountry) && (
                  <p className="text-sm text-stone-400 flex items-center gap-1">
                    <MapPin size={11} className="flex-shrink-0" />
                    {[plant.geoCity, plant.geoState, plant.geoCountry].filter(Boolean).join(", ")}
                  </p>
                )}
              </div>
            )}
          </div>

          {/* Job status */}
          <PlantStatusCard status={analysis?.status ?? null} />

          {/* Species + Care panels — fill remaining height, scroll internally */}
          <div className="grid grid-cols-[3fr_2fr] gap-3 flex-1 min-h-0">
            <SpeciesPanel
              analysis={analysis}
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
              onWater={() => waterMutation.mutate()}
              onFertilize={() => fertMutation.mutate()}
              waterPending={waterMutation.isPending}
              fertPending={fertMutation.isPending}
              plantId={plantId}
              onArchive={() => {
                if (confirm("Archive this plant? You can restore it later.")) {
                  archiveMutation.mutate();
                }
              }}
              archivePending={archiveMutation.isPending}
              onRefresh={() => reanalysisMutation.mutate()}
              refreshPending={reanalysisMutation.isPending}
            />
          </div>
        </div>
      </div>
    </main>
  );
}

// ── Sub-components ───────────────────────────────────────────────────────────

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
  const hasSummaryText = Boolean(historySummaryText?.trim());
  const displayError = historySummaryError ?? summaryMutationError;

  return (
    <div className="rounded-2xl border border-stone-200 bg-white p-3 h-full overflow-y-auto space-y-3">
      <p className="text-xs font-medium uppercase tracking-wide text-stone-400">About This Plant</p>

      {!speciesReady ? (
        <p className="text-sm text-stone-400 italic">Species profile is being prepared…</p>
      ) : (
        <>
          {(analysis.scientificName || analysis.className) && (
            <div className="text-sm text-stone-600">
              {analysis.scientificName && (
                <p className="italic">{analysis.scientificName}</p>
              )}
              {analysis.className && (
                <p className="text-stone-400 text-xs mt-0.5">{analysis.className}</p>
              )}
            </div>
          )}

          {analysis.nativeRegions && analysis.nativeRegions.length > 0 && (
            <FactRow
              icon={<Globe size={14} className="text-stone-400" />}
              label="Native to"
              value={analysis.nativeRegions.join(", ")}
            />
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

          {analysis.propagationInstructions && (
            <FactRow
              icon={<BookOpen size={14} className="text-stone-400" />}
              label="Propagation"
              value={analysis.propagationInstructions}
            />
          )}
        </>
      )}

      <div className="pt-3 mt-1 border-t border-stone-100 space-y-2">
        <div className="flex items-center justify-between gap-2 flex-wrap">
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
          <p className="text-sm text-red-600 leading-snug" role="alert">
            {displayError}
          </p>
        )}
        {hasSummaryText ? (
          <div className="space-y-1">
            <p className="text-sm text-stone-600 leading-relaxed whitespace-pre-wrap">{historySummaryText}</p>
            {historySummaryCompletedAt && (
              <p className="text-[10px] text-stone-400">
                Updated {new Date(historySummaryCompletedAt).toLocaleString()}
              </p>
            )}
          </div>
        ) : (
          !displayError && (
            <p className="text-sm text-stone-400 italic">
              {historySummaryEligible
                ? "Tap Generate summary to build a short narrative from your journal, photos, and care log."
                : "Add a journal note or record watering, fertilizer, or pruning before you can generate a history summary."}
            </p>
          )
        )}
      </div>
    </div>
  );
}

function CarePanel({
  analysis,
  reminderState,
  onWater,
  onFertilize,
  waterPending,
  fertPending,
  plantId,
  onArchive,
  archivePending,
  onRefresh,
  refreshPending,
}: {
  analysis: PlantDetailResponse["latestAnalysis"];
  reminderState: PlantDetailResponse["reminderState"];
  onWater: () => void;
  onFertilize: () => void;
  waterPending: boolean;
  fertPending: boolean;
  plantId: number;
  onArchive: () => void;
  archivePending: boolean;
  onRefresh: () => void;
  refreshPending: boolean;
}) {
  const ready = analysis?.status === "COMPLETED";

  return (
    <div className="rounded-2xl border border-stone-200 bg-white p-3 h-full overflow-y-auto space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs font-medium uppercase tracking-wide text-stone-400">Care</p>
        <ReminderIconRow reminderState={reminderState} size={20} />
      </div>

      {!ready ? (
        <p className="text-sm text-stone-400 italic">Care profile is being prepared…</p>
      ) : (
        <div className="space-y-2">
          {reminderState?.nextWateringInstruction && (
            <CareRow
              icon={<Droplets size={14} className="text-sky-500" />}
              label="Watering"
              value={reminderState.nextWateringInstruction}
              highlight={reminderState.wateringDue || reminderState.wateringOverdue}
              detail={[
                analysis?.wateringFrequency ? `Frequency: ${analysis.wateringFrequency}` : null,
                analysis?.wateringGuidance ?? null,
              ].filter(Boolean).join(" · ") || undefined}
            />
          )}
          {reminderState?.nextFertilizerInstruction && (
            <CareRow
              icon={<Leaf size={14} className="text-amber-600" />}
              label="Fertilizer"
              value={reminderState.nextFertilizerInstruction}
              highlight={reminderState.fertilizerDue}
              detail={[
                analysis?.fertilizerGuidance,
                analysis?.fertilizerFrequency ? `Frequency: ${analysis.fertilizerFrequency}` : null,
              ].filter(Boolean).join(" · ") || undefined}
            />
          )}
          {reminderState?.nextPruningInstruction && (
            <CareRow
              icon={<Scissors size={14} className="text-green-600" />}
              label="Pruning"
              value={reminderState.nextPruningInstruction}
              highlight={reminderState.pruningDue}
            />
          )}
          {analysis?.lightNeeds && (
            <CareRow
              icon={<Sun size={14} className="text-amber-400" />}
              label="Light"
              value={analysis.lightNeeds}
            />
          )}
          {analysis?.placementGuidance && (
            <CareRow
              icon={<MapPin size={14} className="text-stone-400" />}
              label="Placement"
              value={analysis.placementGuidance}
            />
          )}
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
        <div className="grid grid-cols-2 gap-2">
          <Button
            variant="outline" size="sm"
            onClick={onWater} disabled={waterPending}
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
        </div>
        <ButtonLink
          href={`/plants/${plantId}/pruning`}
          variant="outline" size="sm"
          className="w-full text-green-700 border-green-200 hover:bg-green-50 text-xs"
        >
          <Scissors size={13} className="mr-1" />
          Pruning analysis
        </ButtonLink>
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
    </div>
  );
}

function CareRow({ icon, label, value, highlight, detail }: {
  icon: React.ReactNode; label: string; value: string; highlight?: boolean; detail?: string;
}) {
  return (
    <div className={`flex gap-2 text-sm ${highlight ? "text-stone-800" : "text-stone-600"}`}>
      <span className="mt-0.5 flex-shrink-0">{icon}</span>
      <div>
        <span className="text-xs font-medium uppercase tracking-wide text-stone-400 block mb-0.5">
          {label}
        </span>
        <span>{value}</span>
        {detail && (
          <p className="text-xs text-stone-400 mt-1 leading-snug">{detail}</p>
        )}
      </div>
    </div>
  );
}

function FactRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex gap-2 text-sm text-stone-600">
      <span className="mt-0.5 flex-shrink-0">{icon}</span>
      <div>
        <span className="text-xs font-medium uppercase tracking-wide text-stone-400 block mb-0.5">
          {label}
        </span>
        <span>{value}</span>
      </div>
    </div>
  );
}

function HistorySection({
  entries,
  heroImageUrl,
  onAddNote,
  onAddImage,
  addNotePending,
  addImagePending,
}: {
  entries: PlantHistoryEntryDto[];
  heroImageUrl: string | null;
  onAddNote: (text: string) => void;
  onAddImage: (image: File, noteText?: string) => void;
  addNotePending: boolean;
  addImagePending: boolean;
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
    onAddImage(file);
    e.target.value = "";
  }

  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-stone-400 mb-2">
        History
      </p>

      {/* Entry list — scrolls when full */}
      {entries.length > 0 && (
        <div className="max-h-28 overflow-y-auto mb-2 space-y-2">
          {entries.map((entry) => (
            <HistoryEntryRow
              key={entry.id}
              entry={entry}
              sameAsHero={
                heroImageUrl != null &&
                entry.image != null &&
                entry.image.url === heroImageUrl
              }
            />
          ))}
        </div>
      )}

      {/* Input row */}
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
              onClick={() => fileInputRef.current?.click()}
              disabled={addImagePending}
              className="p-1.5 rounded-full text-stone-400 hover:text-stone-600 hover:bg-stone-100 transition-colors disabled:opacity-40"
              title="Add photo"
            >
              <Camera size={16} />
            </button>
            <button
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
    </div>
  );
}

function HistoryEntryRow({
  entry,
  sameAsHero,
}: {
  entry: PlantHistoryEntryDto;
  sameAsHero?: boolean;
}) {
  const date = new Date(entry.createdAt).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  return (
    <div className="flex gap-3 items-start py-2 border-b border-stone-100 last:border-0">
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
