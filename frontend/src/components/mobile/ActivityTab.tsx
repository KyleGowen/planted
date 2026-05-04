"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import Image from "next/image";
import { Droplets, Leaf, Scissors, Sprout } from "lucide-react";
import { cn } from "@/lib/utils";
import { Skeleton } from "@/components/ui/skeleton";
import { listActivity } from "@/lib/api";
import { plantImageSrc } from "@/lib/plantMediaUrl";
import type { ActivityEntryDto } from "@/types/plant";

type FilterKind = "ALL" | "WATERING" | "FERTILIZER" | "PRUNE" | "JOURNAL";

const FILTER_CHIPS: { id: FilterKind; label: string }[] = [
  { id: "ALL", label: "All" },
  { id: "WATERING", label: "Watering" },
  { id: "FERTILIZER", label: "Fertilizing" },
  { id: "PRUNE", label: "Pruning" },
  { id: "JOURNAL", label: "Notes" },
];

function formatRelativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days === 1) return "yesterday";
  if (days < 7) return `${days} days ago`;
  return new Date(iso).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  });
}

function entryIcon(kind: string) {
  switch (kind) {
    case "WATERING":
      return <Droplets size={18} className="text-[hsl(var(--watering))]" />;
    case "FERTILIZER":
      return <Leaf size={18} className="text-[hsl(var(--fertilizer))]" />;
    case "PRUNE":
      return <Scissors size={18} className="text-[hsl(var(--pruning))]" />;
    default:
      return <Sprout size={18} className="text-stone-400" />;
  }
}

function ActivityRow({ entry }: { entry: ActivityEntryDto }) {
  return (
    <div className="flex items-start gap-3 py-3 px-4 border-b border-stone-100 last:border-0">
      {/* Plant thumbnail */}
      <div className="flex-shrink-0 w-10 h-10 rounded-lg overflow-hidden bg-stone-100">
        {entry.plantThumbnail ? (
          <Image
            src={plantImageSrc(entry.plantThumbnail.url)}
            alt={entry.plantName ?? ""}
            width={40}
            height={40}
            className="object-cover w-full h-full"
            loading="lazy"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center">
            <Sprout size={16} className="text-stone-300" />
          </div>
        )}
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5 mb-0.5">
          {entryIcon(entry.entryKind)}
          <span className="text-xs font-semibold text-stone-700 truncate flex-1">
            {entry.plantName ?? "Unknown plant"}
          </span>
          <span className="text-[11px] text-stone-400 flex-shrink-0">
            {formatRelativeTime(entry.createdAt)}
          </span>
        </div>
        {entry.noteText && (
          <p className="text-xs text-stone-500 leading-relaxed truncate">{entry.noteText}</p>
        )}
      </div>

      {/* Event photo thumbnail */}
      {entry.image && (
        <div className="flex-shrink-0 w-10 h-10 rounded-lg overflow-hidden bg-stone-100">
          <Image
            src={plantImageSrc(entry.image.url)}
            alt=""
            width={40}
            height={40}
            className="object-cover w-full h-full"
            loading="lazy"
          />
        </div>
      )}
    </div>
  );
}

export function ActivityTab() {
  const { data: entries, isLoading, error } = useQuery({
    queryKey: ["activity"],
    queryFn: () => listActivity(100),
    staleTime: 30_000,
  });

  const [activeFilter, setActiveFilter] = useState<FilterKind>("ALL");

  const filtered = useMemo(() => {
    if (!entries) return [];
    if (activeFilter === "ALL") return entries;
    return entries.filter((e) => e.entryKind === activeFilter);
  }, [entries, activeFilter]);

  return (
    <div className="h-full flex flex-col overflow-hidden bg-stone-50">
      {/* Header */}
      <div className="px-4 pt-4 pb-2">
        <h1 className="text-lg font-semibold text-stone-800 tracking-tight">Activity</h1>
      </div>

      {/* Filter chips */}
      <div className="px-4 pb-3 flex gap-2 overflow-x-auto scrollbar-none">
        {FILTER_CHIPS.map((chip) => (
          <button
            key={chip.id}
            type="button"
            onClick={() => setActiveFilter(chip.id)}
            className={cn(
              "flex-shrink-0 px-3.5 py-1.5 rounded-full text-xs font-medium min-h-[32px]",
              "border transition-colors duration-150",
              activeFilter === chip.id
                ? "bg-[hsl(var(--primary))] text-white border-[hsl(var(--primary))]"
                : "bg-white text-stone-600 border-stone-200 active:bg-stone-50"
            )}
          >
            {chip.label}
          </button>
        ))}
      </div>

      {/* Feed */}
      <div className="flex-1 overflow-y-auto bg-white rounded-t-2xl mx-2 border border-stone-100">
        {isLoading && (
          <div className="p-4 space-y-4">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="flex items-center gap-3">
                <Skeleton className="w-10 h-10 rounded-lg flex-shrink-0" />
                <div className="flex-1 space-y-1.5">
                  <Skeleton className="h-3 w-3/4" />
                  <Skeleton className="h-3 w-1/2" />
                </div>
              </div>
            ))}
          </div>
        )}

        {error && (
          <p className="p-4 text-sm text-amber-700">
            Could not load activity. Please try again.
          </p>
        )}

        {!isLoading && !error && entries?.length === 0 && (
          <div className="flex flex-col items-center justify-center pt-16 gap-2 text-center px-4">
            <p className="text-stone-400 text-sm">No activity yet.</p>
            <p className="text-stone-300 text-xs">
              Log your first watering or fertilizing to see it here.
            </p>
          </div>
        )}

        {!isLoading && !error && filtered.length === 0 && (entries?.length ?? 0) > 0 && (
          <p className="p-6 text-center text-sm text-stone-400">
            No {activeFilter.toLowerCase()} events yet.
          </p>
        )}

        {filtered.map((entry, i) => (
          <ActivityRow key={`${entry.entryKind}-${entry.plantId}-${entry.createdAt}-${i}`} entry={entry} />
        ))}
      </div>
    </div>
  );
}
