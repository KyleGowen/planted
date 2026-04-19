"use client";

import { useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { Skeleton } from "@/components/ui/skeleton";
import { PlantBioView } from "@/components/plant/PlantBioView";
import { getPlant } from "@/lib/api";
import { hasRefreshingBioSections } from "@/lib/bioSections";

export default function PlantDetailPage() {
  const { id } = useParams<{ id: string }>();
  const plantId = Number(id);
  const router = useRouter();

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
      if (data.hasActiveJobs) return 3000;
      // Keep polling while any individual bio section is still being generated
      // so the UI fills in per-section as each narrow prompt completes.
      return hasRefreshingBioSections(data) ? 3000 : false;
    },
  });

  // Escape closes the plant bio and returns to the All plants list.
  // Skip when another handler already consumed Escape (e.g. Radix dialogs call
  // preventDefault) or when focus is in an editable field so inline cancel
  // behaviors (rename, journal) still work.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape" || e.defaultPrevented) return;
      const t = e.target as HTMLElement | null;
      if (
        t &&
        (t.tagName === "INPUT" ||
          t.tagName === "TEXTAREA" ||
          t.isContentEditable)
      ) {
        return;
      }
      router.push("/plants");
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [router]);

  if (isLoading) return <LoadingSkeleton />;
  if (!plant) return null;

  return (
    <main className="h-[100dvh] overflow-hidden flex flex-col relative px-3 py-2">
      {/* Back nav — absolute so it doesn't push content down */}
      <Link
        href="/plants"
        className="absolute top-2 left-3 z-10 inline-flex items-center gap-1 text-xs text-stone-400 hover:text-stone-600"
      >
        <ArrowLeft size={12} /> All plants
      </Link>

      <PlantBioView plant={plant} onArchived={() => router.push("/plants")} />
    </main>
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
