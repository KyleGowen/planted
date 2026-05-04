"use client";

import Image from "next/image";
import { Sprout } from "lucide-react";
import { cn } from "@/lib/utils";
import { plantImageSrc } from "@/lib/plantMediaUrl";
import { ReminderIconRow } from "@/components/plant/ReminderIconRow";
import type { PlantListItemResponse } from "@/types/plant";

interface Props {
  plant: PlantListItemResponse;
  onClick: () => void;
}

function needsAttention(plant: PlantListItemResponse): boolean {
  const s = plant.reminderState;
  if (!s) return false;
  return (
    s.wateringDue ||
    s.wateringOverdue ||
    s.fertilizerDue ||
    s.pruningDue ||
    s.healthAttentionNeeded ||
    s.lightAttentionNeeded ||
    s.placementAttentionNeeded
  );
}

export function MobilePlantCard({ plant, onClick }: Props) {
  const isPending =
    plant.analysisStatus === "PENDING" || plant.analysisStatus === "PROCESSING";

  const thumbnail =
    plant.illustratedImage ??
    (plant.originalImage?.mimeType?.startsWith("image/")
      ? plant.originalImage
      : null);

  const attention = needsAttention(plant);

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "relative w-full rounded-2xl overflow-hidden bg-stone-100 text-left",
        "active:scale-[0.98] transition-transform duration-100",
        "min-h-[44px] shadow-sm border border-stone-200"
      )}
    >
      {/* Photo — 4:3 aspect */}
      <div className="relative w-full aspect-[4/3] overflow-hidden bg-stone-100">
        {thumbnail ? (
          <Image
            src={plantImageSrc(thumbnail.url)}
            alt={plant.displayLabel}
            fill
            className="object-cover"
            sizes="(max-width: 768px) 50vw"
            loading="lazy"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center">
            <Sprout
              size={28}
              className={cn("text-stone-300", isPending && "animate-pulse")}
            />
          </div>
        )}

        {/* Attention dot */}
        {attention && (
          <span className="absolute top-2 right-2 h-2.5 w-2.5 rounded-full bg-amber-500 shadow" />
        )}
      </div>

      {/* Identity overlay */}
      <div className="px-3 py-2.5 space-y-1">
        <p className="font-medium text-stone-800 text-sm truncate leading-tight">
          {plant.name ?? plant.speciesLabel ?? (isPending ? "Identifying…" : "Unknown plant")}
        </p>
        {plant.name && plant.speciesLabel && (
          <p className="text-[11px] text-stone-400 italic truncate species-label">
            {plant.speciesLabel}
          </p>
        )}
        {plant.reminderState && (
          <ReminderIconRow
            reminderState={plant.reminderState}
            className="mt-1"
          />
        )}
      </div>
    </button>
  );
}
