"use client";

import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import Image from "next/image";
import { Sprout, Search } from "lucide-react";
import { cn } from "@/lib/utils";
import { plantImageSrc } from "@/lib/plantMediaUrl";
import { listPlants } from "@/lib/api";
import { ReminderIconRow } from "@/components/plant/ReminderIconRow";
import { Skeleton } from "@/components/ui/skeleton";
import { PlantDetailSheet } from "./PlantDetailSheet";
import type { PlantListItemResponse } from "@/types/plant";

function PlantRow({
  plant,
  onClick,
}: {
  plant: PlantListItemResponse;
  onClick: () => void;
}) {
  const isPending =
    plant.analysisStatus === "PENDING" || plant.analysisStatus === "PROCESSING";

  const thumbnail =
    plant.illustratedImage ??
    (plant.originalImage?.mimeType?.startsWith("image/") ? plant.originalImage : null);

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "w-full flex items-center gap-3 px-4 py-3 min-h-[44px]",
        "border-b border-stone-100 active:bg-stone-50 text-left"
      )}
    >
      {/* Thumbnail */}
      <div className="w-14 h-14 rounded-xl overflow-hidden flex-shrink-0 bg-stone-100">
        {thumbnail ? (
          <Image
            src={plantImageSrc(thumbnail.url)}
            alt={plant.displayLabel}
            width={56}
            height={56}
            className="object-cover w-full h-full"
            loading="lazy"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center">
            <Sprout
              size={20}
              className={cn("text-stone-300", isPending && "animate-pulse")}
            />
          </div>
        )}
      </div>

      {/* Identity */}
      <div className="flex-1 min-w-0">
        {plant.name ? (
          <>
            <p className="font-medium text-stone-800 truncate text-sm">{plant.name}</p>
            {plant.speciesLabel && (
              <p className="text-xs text-stone-400 italic truncate species-label">
                {plant.speciesLabel}
              </p>
            )}
          </>
        ) : (
          <p className="font-medium text-stone-600 italic text-sm truncate species-label">
            {plant.speciesLabel ?? (isPending ? "Identifying…" : "Unknown plant")}
          </p>
        )}
        {plant.reminderState && (
          <ReminderIconRow reminderState={plant.reminderState} className="mt-1" />
        )}
      </div>
    </button>
  );
}

export function MyPlantsTab() {
  const { data: plants, isLoading, error } = useQuery({
    queryKey: ["plants"],
    queryFn: listPlants,
    staleTime: 30_000,
  });

  const [search, setSearch] = useState("");
  const [selectedPlantId, setSelectedPlantId] = useState<number | null>(null);

  const activePlants = useMemo(
    () => (plants ?? []).filter((p) => p.status === "ACTIVE"),
    [plants]
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return activePlants;
    return activePlants.filter(
      (p) =>
        p.displayLabel.toLowerCase().includes(q) ||
        (p.name ?? "").toLowerCase().includes(q) ||
        (p.speciesLabel ?? "").toLowerCase().includes(q)
    );
  }, [activePlants, search]);

  const indoor = filtered.filter((p) => p.growingContext !== "OUTDOOR");
  const outdoor = filtered.filter((p) => p.growingContext === "OUTDOOR");

  return (
    <div className="h-full flex flex-col overflow-hidden bg-stone-50">
      {/* Search bar */}
      <div className="px-4 pt-4 pb-3">
        <div className="relative">
          <Search
            size={16}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-stone-400 pointer-events-none"
          />
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search plants…"
            className={cn(
              "w-full pl-9 pr-4 py-2.5 rounded-xl border border-stone-200 bg-white text-sm",
              "placeholder:text-stone-400 outline-none min-h-[44px]",
              "focus-visible:border-stone-400 focus-visible:ring-2 focus-visible:ring-stone-200/80"
            )}
          />
        </div>
      </div>

      {/* List */}
      <div className="flex-1 overflow-y-auto bg-white border border-stone-100 rounded-t-2xl mx-2">
        {isLoading && (
          <div className="p-4 space-y-3">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-16 rounded-xl" />
            ))}
          </div>
        )}

        {error && (
          <p className="p-4 text-sm text-amber-700">
            Could not load plants. Please try again.
          </p>
        )}

        {!isLoading && !error && activePlants.length === 0 && (
          <p className="p-6 text-center text-sm text-stone-400">
            No plants yet. Tap the camera to add one.
          </p>
        )}

        {!isLoading && !error && activePlants.length > 0 && filtered.length === 0 && (
          <p className="p-6 text-center text-sm text-stone-400">
            No plants match &ldquo;{search}&rdquo;.
          </p>
        )}

        {indoor.length > 0 && (
          <section>
            <h2 className="px-4 py-2 text-[11px] font-semibold text-stone-400 uppercase tracking-wide bg-stone-50 border-b border-stone-100">
              Indoor ({indoor.length})
            </h2>
            {indoor.map((plant) => (
              <PlantRow
                key={plant.id}
                plant={plant}
                onClick={() => setSelectedPlantId(plant.id)}
              />
            ))}
          </section>
        )}

        {outdoor.length > 0 && (
          <section>
            <h2 className="px-4 py-2 text-[11px] font-semibold text-stone-400 uppercase tracking-wide bg-stone-50 border-b border-stone-100">
              Outdoor ({outdoor.length})
            </h2>
            {outdoor.map((plant) => (
              <PlantRow
                key={plant.id}
                plant={plant}
                onClick={() => setSelectedPlantId(plant.id)}
              />
            ))}
          </section>
        )}
      </div>

      {selectedPlantId !== null && (
        <PlantDetailSheet
          plantId={selectedPlantId}
          onClose={() => setSelectedPlantId(null)}
        />
      )}
    </div>
  );
}
