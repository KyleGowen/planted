"use client";

import Link from "next/link";
import Image from "next/image";
import { Sprout } from "lucide-react";
import { ReminderIconRow } from "./ReminderIconRow";
import { cn } from "@/lib/utils";
import type { PlantListItemResponse } from "@/types/plant";

interface Props {
  plant: PlantListItemResponse;
  className?: string;
}

function isImageUrl(mimeType: string | null): boolean {
  return mimeType != null && mimeType.startsWith("image/");
}

export function PlantListItemCard({ plant, className }: Props) {
  const isPending = plant.analysisStatus === "PENDING" || plant.analysisStatus === "PROCESSING";

  const thumbnail =
    plant.illustratedImage ??
    (plant.originalImage && isImageUrl(plant.originalImage.mimeType)
      ? plant.originalImage
      : null);

  return (
    <Link href={`/plants/${plant.id}`} className="block">
      <div
        className={cn(
          "flex items-center gap-4 rounded-xl border border-stone-200 bg-white px-4 py-3",
          "hover:border-stone-300 hover:shadow-sm transition-all",
          className
        )}
      >
        {/* Thumbnail */}
        <div className="relative h-14 w-14 flex-shrink-0 overflow-hidden rounded-lg bg-stone-100">
          {thumbnail ? (
            <Image
              src={thumbnail.url}
              alt={plant.displayLabel}
              fill
              className="object-cover"
              sizes="56px"
            />
          ) : (
            <div className="flex h-full w-full items-center justify-center">
              <Sprout size={20} className={cn("text-stone-300", isPending && "animate-pulse")} />
            </div>
          )}
        </div>

        {/* Identity */}
        <div className="flex-1 min-w-0">
          {plant.name ? (
            <>
              <p className="font-medium text-stone-800 truncate">{plant.name}</p>
              {(plant.genus || plant.species) && (
                <p className="text-xs text-stone-400 italic truncate species-label">
                  {[plant.genus, plant.species].filter(Boolean).join(" ")}
                </p>
              )}
            </>
          ) : (
            <p className="font-medium text-stone-700 italic species-label truncate">
              {plant.speciesLabel ?? (isPending ? "Identifying…" : "Unknown plant")}
            </p>
          )}
          {plant.reminderState && (
            <ReminderIconRow reminderState={plant.reminderState} className="mt-1.5" />
          )}
        </div>

        {/* Analysis pending indicator */}
        {isPending && (
          <span className="flex-shrink-0 h-1.5 w-1.5 rounded-full bg-stone-300 animate-pulse" />
        )}
      </div>
    </Link>
  );
}
