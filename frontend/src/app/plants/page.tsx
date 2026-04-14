"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus, Monitor } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { ButtonLink } from "@/components/ui/button-link";
import { PlantListItemCard } from "@/components/plant/PlantListItemCard";
import { listPlants } from "@/lib/api";

export default function PlantsPage() {
  const { data: plants, isLoading, error } = useQuery({
    queryKey: ["plants"],
    queryFn: listPlants,
    staleTime: 30_000,
  });

  return (
    <main className="mx-auto max-w-2xl px-4 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-semibold text-stone-800 tracking-tight">
            Your Plants
          </h1>
          {plants && (
            <p className="text-sm text-stone-400 mt-0.5">
              {plants.length} {plants.length === 1 ? "plant" : "plants"}
            </p>
          )}
        </div>
        <div className="flex gap-2">
          <ButtonLink href="/screensaver" variant="ghost" size="sm" className="text-stone-500">
            <Monitor size={16} className="mr-1.5" />
            Screensaver
          </ButtonLink>
          <ButtonLink href="/plants/upload" size="sm">
            <Plus size={16} className="mr-1.5" />
            Add plant
          </ButtonLink>
        </div>
      </div>

      {/* Loading state */}
      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-20 rounded-xl" />
          ))}
        </div>
      )}

      {/* Error state */}
      {error && (
        <div className="rounded-xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-700">
          Could not load your plants. Please check your connection and try again.
        </div>
      )}

      {/* Empty state */}
      {!isLoading && !error && plants?.length === 0 && (
        <div className="text-center py-16">
          <p className="text-stone-400 text-sm mb-4">No plants yet.</p>
          <ButtonLink href="/plants/upload" size="sm">Add your first plant</ButtonLink>
        </div>
      )}

      {/* Plant list */}
      {plants && plants.length > 0 && (
        <div className="space-y-2.5">
          {plants.map((plant) => (
            <PlantListItemCard key={plant.id} plant={plant} />
          ))}
        </div>
      )}
    </main>
  );
}
