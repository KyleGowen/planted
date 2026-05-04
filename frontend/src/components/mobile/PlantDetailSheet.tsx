"use client";

import { useRef, useState, useEffect, useCallback } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Image from "next/image";
import {
  X, Droplets, Leaf, Camera, Sprout, Scissors, Upload,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { ReminderIconRow } from "@/components/plant/ReminderIconRow";
import { Skeleton } from "@/components/ui/skeleton";
import { getPlant, recordWatering, recordFertilizer, uploadPlantPhoto, addHistoryNote } from "@/lib/api";
import { plantImageSrc } from "@/lib/plantMediaUrl";
import { CareObservationInput } from "@/components/plant/CareObservationInput";
import type { PlantHistoryEntryDto } from "@/types/plant";

interface Props {
  plantId: number;
  onClose: () => void;
}

const DISMISS_THRESHOLD = 80; // px drag to dismiss

function formatRelativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function entryKindLabel(kind: string | undefined): string {
  switch (kind) {
    case "WATERING": return "Watered";
    case "FERTILIZER": return "Fertilized";
    case "PRUNE": return "Pruned";
    default: return "Note";
  }
}

function HistoryEntry({ entry }: { entry: PlantHistoryEntryDto }) {
  return (
    <div className="flex gap-3 py-2.5 border-b border-stone-100 last:border-0">
      {/* Kind icon */}
      <div className="flex-shrink-0 w-8 h-8 rounded-full bg-stone-100 flex items-center justify-center mt-0.5">
        {entry.entryKind === "WATERING" ? (
          <Droplets size={14} className="text-[hsl(var(--watering))]" />
        ) : entry.entryKind === "FERTILIZER" ? (
          <Leaf size={14} className="text-[hsl(var(--fertilizer))]" />
        ) : entry.entryKind === "PRUNE" ? (
          <Scissors size={14} className="text-[hsl(var(--pruning))]" />
        ) : (
          <Sprout size={14} className="text-stone-400" />
        )}
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-stone-600">
            {entryKindLabel(entry.entryKind)}
          </span>
          <span className="text-[11px] text-stone-400">{formatRelativeTime(entry.createdAt)}</span>
        </div>
        {entry.noteText && (
          <p className="text-xs text-stone-500 mt-0.5 leading-relaxed">{entry.noteText}</p>
        )}
      </div>

      {/* Photo thumbnail */}
      {entry.image && (
        <div className="flex-shrink-0 w-12 h-12 rounded-lg overflow-hidden bg-stone-100">
          <Image
            src={plantImageSrc(entry.image.url)}
            alt=""
            width={48}
            height={48}
            className="object-cover w-full h-full"
            loading="lazy"
          />
        </div>
      )}
    </div>
  );
}

export function PlantDetailSheet({ plantId, onClose }: Props) {
  const queryClient = useQueryClient();
  const { data: plant, isLoading } = useQuery({
    queryKey: ["plant", plantId],
    queryFn: () => getPlant(plantId),
    staleTime: 30_000,
  });

  // Sheet visibility & swipe-to-dismiss
  const [visible, setVisible] = useState(false);
  const [showPhotoSourcePicker, setShowPhotoSourcePicker] = useState(false);
  const [dragY, setDragY] = useState(0);
  const touchStartY = useRef<number | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Trigger entrance animation on mount
    const frame = requestAnimationFrame(() => setVisible(true));
    return () => cancelAnimationFrame(frame);
  }, []);

  const dismiss = useCallback(() => {
    setShowPhotoSourcePicker(false);
    setVisible(false);
    setTimeout(onClose, 300); // wait for exit animation
  }, [onClose]);

  const handleTouchStart = useCallback((e: React.TouchEvent) => {
    touchStartY.current = e.touches[0].clientY;
  }, []);

  const handleTouchMove = useCallback((e: React.TouchEvent) => {
    if (touchStartY.current === null) return;
    const delta = e.touches[0].clientY - touchStartY.current;
    if (delta > 0) setDragY(delta);
  }, []);

  const handleTouchEnd = useCallback(() => {
    if (dragY >= DISMISS_THRESHOLD) {
      dismiss();
    }
    touchStartY.current = null;
    setDragY(0);
  }, [dragY, dismiss]);

  // Add photo: camera capture vs photo library (separate inputs — capture forces camera-only)
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const libraryInputRef = useRef<HTMLInputElement>(null);
  const [actionFeedback, setActionFeedback] = useState<string | null>(null);

  function showFeedback(msg: string) {
    setActionFeedback(msg);
    setTimeout(() => setActionFeedback(null), 2000);
  }

  const waterMutation = useMutation({
    mutationFn: () => recordWatering(plantId, { wateredAt: new Date().toISOString() }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      showFeedback("Watered!");
    },
  });

  const fertilizeMutation = useMutation({
    mutationFn: () => recordFertilizer(plantId, { fertilizedAt: new Date().toISOString() }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      showFeedback("Fertilized!");
    },
  });

  const photoMutation = useMutation({
    mutationFn: (file: File) => uploadPlantPhoto(plantId, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      showFeedback("Photo added!");
    },
  });

  const addNoteMutation = useMutation({
    mutationFn: (noteText: string) => addHistoryNote(plantId, noteText),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plant", plantId] });
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      queryClient.invalidateQueries({ queryKey: ["activity"] });
      addNoteMutation.reset();
      showFeedback("Note saved!");
    },
  });

  function handlePhotoFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) photoMutation.mutate(file);
    e.target.value = "";
  }

  function triggerCameraPick() {
    setShowPhotoSourcePicker(false);
    requestAnimationFrame(() => cameraInputRef.current?.click());
  }

  function triggerLibraryPick() {
    setShowPhotoSourcePicker(false);
    requestAnimationFrame(() => libraryInputRef.current?.click());
  }

  const thumbnail = plant?.illustratedImage ?? plant?.originalImages?.[0] ?? null;
  const displayName = plant?.name ?? plant?.speciesLabel ?? (isLoading ? null : "Unknown plant");

  const panelTransform = visible
    ? `translateY(${dragY}px)`
    : "translateY(100%)";

  return (
    <>
      {/* Scrim */}
      <div
        className={cn(
          "fixed inset-0 z-40 bg-black/40 transition-opacity duration-300",
          visible ? "opacity-100" : "opacity-0"
        )}
        onClick={dismiss}
        aria-hidden="true"
      />

      {/* Panel */}
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={displayName ?? "Plant detail"}
        className={cn(
          "fixed inset-x-0 bottom-0 z-50 flex flex-col",
          "bg-white rounded-t-2xl shadow-xl max-h-[92dvh]",
          "transition-transform duration-300 ease-out"
        )}
        style={{
          transform: panelTransform,
          paddingBottom: "env(safe-area-inset-bottom, 0px)",
        }}
      >
        {/* Drag handle — swipe-to-dismiss target */}
        <div
          className="flex justify-center pt-3 pb-2 cursor-pointer"
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          onClick={dismiss}
        >
          <div className="w-10 h-1 rounded-full bg-stone-200" />
        </div>

        {/* Close button */}
        <button
          type="button"
          onClick={dismiss}
          aria-label="Close"
          className="absolute top-3 right-4 min-h-[44px] min-w-[44px] flex items-center justify-center text-stone-400"
        >
          <X size={20} />
        </button>

        {/* Scrollable body */}
        <div className="flex-1 overflow-y-auto overscroll-y-contain">
          {isLoading && (
            <div className="p-4 space-y-3">
              <Skeleton className="aspect-[16/9] w-full rounded-xl" />
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-4 w-1/2" />
            </div>
          )}

          {plant && (
            <>
              {/* Hero image — 16:9 */}
              {thumbnail && (
                <div className="relative w-full aspect-[16/9] overflow-hidden bg-stone-100 mx-0">
                  <Image
                    src={plantImageSrc(thumbnail.url)}
                    alt={displayName ?? ""}
                    fill
                    className="object-cover"
                    sizes="100vw"
                    priority
                  />
                </div>
              )}

              <div className="px-4 pt-4 pb-2">
                {/* Name + species */}
                <h2 className="text-xl font-semibold text-stone-800 leading-tight">
                  {displayName}
                </h2>
                {plant.speciesLabel && plant.name && (
                  <p className="text-sm text-stone-400 italic species-label mt-0.5">
                    {plant.speciesLabel}
                  </p>
                )}
                {plant.location && (
                  <p className="text-xs text-stone-400 mt-1">{plant.location}</p>
                )}

                {plant.reminderState && (
                  <ReminderIconRow
                    reminderState={plant.reminderState}
                    className="mt-2"
                  />
                )}

                {/* Action feedback flash */}
                {actionFeedback && (
                  <p className="mt-2 text-sm font-medium text-[hsl(var(--primary))] animate-in fade-in-0 duration-200">
                    {actionFeedback}
                  </p>
                )}

                {/* Quick actions */}
                <div className="flex gap-2 mt-4">
                  <button
                    type="button"
                    onClick={() => waterMutation.mutate()}
                    disabled={waterMutation.isPending}
                    className={cn(
                      "flex-1 flex flex-col items-center gap-1 rounded-xl py-3 min-h-[44px]",
                      "bg-stone-50 border border-stone-200 text-stone-600",
                      "active:bg-stone-100 transition-colors disabled:opacity-50"
                    )}
                  >
                    <Droplets size={18} className="text-[hsl(var(--watering))]" />
                    <span className="text-[11px] font-medium">Water</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => fertilizeMutation.mutate()}
                    disabled={fertilizeMutation.isPending}
                    className={cn(
                      "flex-1 flex flex-col items-center gap-1 rounded-xl py-3 min-h-[44px]",
                      "bg-stone-50 border border-stone-200 text-stone-600",
                      "active:bg-stone-100 transition-colors disabled:opacity-50"
                    )}
                  >
                    <Leaf size={18} className="text-[hsl(var(--fertilizer))]" />
                    <span className="text-[11px] font-medium">Fertilize</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setShowPhotoSourcePicker(true)}
                    disabled={photoMutation.isPending}
                    className={cn(
                      "flex-1 flex flex-col items-center gap-1 rounded-xl py-3 min-h-[44px]",
                      "bg-stone-50 border border-stone-200 text-stone-600",
                      "active:bg-stone-100 transition-colors disabled:opacity-50"
                    )}
                  >
                    <Camera size={18} className="text-stone-500" />
                    <span className="text-[11px] font-medium">Add Photo</span>
                  </button>
                </div>

                <div className="mt-4">
                  <CareObservationInput
                    onAddNote={(text) => addNoteMutation.mutate(text)}
                    addNotePending={addNoteMutation.isPending}
                    noteError={
                      addNoteMutation.error instanceof Error
                        ? addNoteMutation.error.message
                        : null
                    }
                  />
                </div>
              </div>

              {/* History log */}
              <div className="px-4 pt-3 pb-4">
                <h3 className="text-xs font-semibold text-stone-500 uppercase tracking-wide mb-1">
                  History
                </h3>
                {plant.historyEntries.length === 0 ? (
                  <p className="text-xs text-stone-400 py-2">
                    No history yet — add a note above.
                  </p>
                ) : (
                  plant.historyEntries.map((entry) => (
                    <HistoryEntry key={entry.id} entry={entry} />
                  ))
                )}
              </div>
            </>
          )}
        </div>
      </div>

      {/* Hidden file inputs live outside the picker so they persist when overlay unmount timing varies */}
      <input
        ref={cameraInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        className="hidden"
        onChange={handlePhotoFile}
        aria-hidden="true"
        tabIndex={-1}
      />
      <input
        ref={libraryInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={handlePhotoFile}
        aria-hidden="true"
        tabIndex={-1}
      />

      {/* Photo source: camera vs library (stacked above plant sheet) */}
      {showPhotoSourcePicker && (
        <>
          <div
            className="fixed inset-0 z-[60] bg-black/40 animate-in fade-in-0 duration-200"
            onClick={() => setShowPhotoSourcePicker(false)}
            aria-hidden="true"
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="photo-source-sheet-title"
            className={cn(
              "fixed inset-x-0 bottom-0 z-[61] mx-auto w-full max-w-lg",
              "bg-white rounded-t-2xl shadow-xl px-4 pt-5 pb-4",
              "animate-in fade-in-0 slide-in-from-bottom-2 duration-200"
            )}
            style={{
              paddingBottom: "max(1rem, env(safe-area-inset-bottom, 0px))",
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h2
              id="photo-source-sheet-title"
              className="text-center text-base font-semibold text-stone-800 mb-4"
            >
              Add photo
            </h2>
            <div className="flex flex-col gap-2">
              <button
                type="button"
                onClick={triggerCameraPick}
                disabled={photoMutation.isPending}
                className={cn(
                  "w-full flex items-center justify-center gap-2 rounded-xl py-3 min-h-[44px]",
                  "bg-stone-50 border border-stone-200 text-stone-600",
                  "active:bg-stone-100 transition-colors disabled:opacity-50",
                  "text-sm font-semibold"
                )}
              >
                <Camera size={18} className="text-stone-500 shrink-0" />
                Take picture
              </button>
              <button
                type="button"
                onClick={triggerLibraryPick}
                disabled={photoMutation.isPending}
                className={cn(
                  "w-full flex items-center justify-center gap-2 rounded-xl py-3 min-h-[44px]",
                  "bg-stone-50 border border-stone-200 text-stone-600",
                  "active:bg-stone-100 transition-colors disabled:opacity-50",
                  "text-sm font-semibold"
                )}
              >
                <Upload size={18} className="text-stone-500 shrink-0" />
                Library
              </button>
              <button
                type="button"
                onClick={() => setShowPhotoSourcePicker(false)}
                className="w-full py-3 min-h-[44px] text-sm font-medium text-stone-500 active:bg-stone-50 rounded-xl transition-colors"
              >
                Cancel
              </button>
            </div>
          </div>
        </>
      )}
    </>
  );
}
