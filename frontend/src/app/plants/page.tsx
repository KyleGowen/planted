"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Monitor } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { ButtonLink } from "@/components/ui/button-link";
import { PlantListItemCard } from "@/components/plant/PlantListItemCard";
import { cn } from "@/lib/utils";
import { getUserLocation, listPlants, putUserLocation } from "@/lib/api";

export default function PlantsPage() {
  const queryClient = useQueryClient();
  const { data: plants, isLoading, error } = useQuery({
    queryKey: ["plants"],
    queryFn: listPlants,
    staleTime: 30_000,
  });

  const {
    data: locationData,
    isLoading: locationLoading,
    error: locationError,
  } = useQuery({
    queryKey: ["userLocation"],
    queryFn: getUserLocation,
    staleTime: 60_000,
  });

  const [locationDraft, setLocationDraft] = useState("");
  const [locationSavedFlash, setLocationSavedFlash] = useState(false);
  const savedFlashTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (locationData !== undefined) {
      setLocationDraft(locationData.address ?? "");
    }
  }, [locationData]);

  useEffect(() => {
    return () => {
      if (savedFlashTimer.current) clearTimeout(savedFlashTimer.current);
    };
  }, []);

  const saveLocationMutation = useMutation({
    mutationFn: putUserLocation,
    onSuccess: (res) => {
      queryClient.setQueryData(["userLocation"], res);
      if (savedFlashTimer.current) clearTimeout(savedFlashTimer.current);
      setLocationSavedFlash(true);
      savedFlashTimer.current = setTimeout(() => {
        setLocationSavedFlash(false);
        savedFlashTimer.current = null;
      }, 2000);
    },
  });

  const handleLocationBlur = () => {
    const trimmed = locationDraft.trim();
    const saved = (locationData?.address ?? "").trim();
    if (trimmed === saved) return;
    saveLocationMutation.mutate({
      address: trimmed.length > 0 ? trimmed : null,
    });
  };

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

      {/* Your Location — shared across plants for climate-aware care prompts */}
      <div className="mb-6 space-y-1.5">
        <label
          htmlFor="user-location"
          className="text-sm font-medium text-stone-700"
        >
          Your Location
        </label>
        {locationLoading ? (
          <Skeleton className="h-20 w-full rounded-xl" />
        ) : (
          <textarea
            id="user-location"
            rows={2}
            placeholder="e.g. 123 Oak St, Portland, OR"
            value={locationDraft}
            onChange={(e) => setLocationDraft(e.target.value)}
            onBlur={handleLocationBlur}
            disabled={saveLocationMutation.isPending}
            className={cn(
              "w-full resize-y rounded-xl border border-stone-200 bg-white px-3 py-2 text-sm text-stone-800",
              "placeholder:text-stone-400 outline-none transition-colors",
              "focus-visible:border-stone-400 focus-visible:ring-2 focus-visible:ring-stone-200/80",
              "disabled:opacity-60"
            )}
          />
        )}
        <p className="text-xs text-stone-400 leading-relaxed">
          Optional. We send this with care and history prompts so advice can reflect your typical
          climate and seasons. Leave blank to omit it.
        </p>
        {locationError && (
          <p className="text-xs text-amber-700">
            Could not load saved location. You can still type an address; it may not persist until
            the server is reachable.
          </p>
        )}
        {saveLocationMutation.isError && (
          <p className="text-xs text-amber-700">
            Could not save location. Check your connection and try leaving the field again.
          </p>
        )}
        {locationSavedFlash && (
          <p className="text-xs text-stone-500" role="status">
            Saved
          </p>
        )}
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
