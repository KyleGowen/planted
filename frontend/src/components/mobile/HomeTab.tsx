"use client";

import { useRef, useState, useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Skeleton } from "@/components/ui/skeleton";
import { MobilePlantCard } from "./MobilePlantCard";
import { PlantDetailSheet } from "./PlantDetailSheet";
import { listPlants } from "@/lib/api";

const PULL_THRESHOLD = 60; // px to trigger refresh

export function HomeTab() {
  const queryClient = useQueryClient();
  const { data: plants, isLoading, error } = useQuery({
    queryKey: ["plants"],
    queryFn: listPlants,
    staleTime: 30_000,
  });

  const [selectedPlantId, setSelectedPlantId] = useState<number | null>(null);

  // Pull-to-refresh state
  const touchStartY = useRef<number | null>(null);
  const [pullDistance, setPullDistance] = useState(0);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const handleTouchStart = useCallback((e: React.TouchEvent) => {
    const el = scrollRef.current;
    if (el && el.scrollTop === 0) {
      touchStartY.current = e.touches[0].clientY;
    }
  }, []);

  const handleTouchMove = useCallback((e: React.TouchEvent) => {
    if (touchStartY.current === null) return;
    const delta = e.touches[0].clientY - touchStartY.current;
    if (delta > 0) {
      setPullDistance(Math.min(delta, PULL_THRESHOLD * 1.5));
    }
  }, []);

  const handleTouchEnd = useCallback(async () => {
    if (pullDistance >= PULL_THRESHOLD && !isRefreshing) {
      setIsRefreshing(true);
      await queryClient.invalidateQueries({ queryKey: ["plants"] });
      setIsRefreshing(false);
    }
    touchStartY.current = null;
    setPullDistance(0);
  }, [pullDistance, isRefreshing, queryClient]);

  const activePlants = (plants ?? []).filter((p) => p.status === "ACTIVE");

  return (
    <div className="h-full flex flex-col overflow-hidden bg-stone-50">
      {/* Pull-to-refresh indicator */}
      {pullDistance > 0 && (
        <div
          className="flex items-center justify-center text-xs text-stone-400 transition-all bg-stone-50"
          style={{ height: `${pullDistance}px` }}
        >
          {pullDistance >= PULL_THRESHOLD ? "Release to refresh" : "Pull to refresh"}
        </div>
      )}
      {isRefreshing && (
        <div className="flex items-center justify-center h-8 text-xs text-stone-400 bg-stone-50">
          Refreshing…
        </div>
      )}

      {/* Header */}
      <div className="px-4 pt-4 pb-2 flex items-center justify-between">
        <h1 className="text-lg font-semibold text-stone-800 tracking-tight">My Garden</h1>
        {plants && (
          <span className="text-xs text-stone-400">{activePlants.length} plants</span>
        )}
      </div>

      {/* Scrollable grid */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto overscroll-y-contain"
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
      >
        <div className="px-4 pb-4">
          {/* Loading skeletons */}
          {isLoading && (
            <div className="grid grid-cols-2 gap-3">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="aspect-[4/3] rounded-2xl" />
              ))}
            </div>
          )}

          {/* Error */}
          {error && (
            <div className="rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-700 mt-2">
              Could not load plants. Pull down to retry.
            </div>
          )}

          {/* Empty */}
          {!isLoading && !error && activePlants.length === 0 && (
            <div className="flex flex-col items-center justify-center pt-16 gap-3 text-center">
              <p className="text-stone-400 text-sm">No plants yet.</p>
              <p className="text-stone-300 text-xs">Tap the camera to add your first plant</p>
            </div>
          )}

          {/* Grid */}
          {activePlants.length > 0 && (
            <div className="grid grid-cols-2 gap-3">
              {activePlants.map((plant) => (
                <MobilePlantCard
                  key={plant.id}
                  plant={plant}
                  onClick={() => setSelectedPlantId(plant.id)}
                />
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Plant detail bottom sheet */}
      {selectedPlantId !== null && (
        <PlantDetailSheet
          plantId={selectedPlantId}
          onClose={() => setSelectedPlantId(null)}
        />
      )}
    </div>
  );
}
