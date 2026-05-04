"use client";

import { useEffect, useState, useCallback, useMemo } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getPlant, listPlants } from "@/lib/api";
import { plantImageSrc } from "@/lib/plantMediaUrl";
import type { PlantImageDto, PlantListItemResponse } from "@/types/plant";
import { PlantBioView } from "@/components/plant/PlantBioView";

const DISPLAY_DURATION_MS = 60_000;

function shuffle<T>(arr: T[]): T[] {
  const copy = [...arr];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

export default function ScreensaverPage() {
  const router = useRouter();
  const [currentIndex, setCurrentIndex] = useState(0);

  const { data: plants } = useQuery({
    queryKey: ["plants"],
    queryFn: listPlants,
    staleTime: 60_000,
  });

  // Reshuffle only when the set of plant IDs changes, so background refetches
  // don't jump the user to a new slide mid-viewing.
  const plantIdKey = useMemo(() => {
    if (!plants || plants.length === 0) return "";
    return [...plants.map((p) => p.id)].sort((a, b) => a - b).join(",");
  }, [plants]);

  const queue = useMemo<PlantListItemResponse[]>(() => {
    if (!plants || plants.length === 0) return [];
    return shuffle(plants);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [plantIdKey]);

  const current = queue.length > 0 ? queue[currentIndex % queue.length] : undefined;
  const currentPlantId = current?.id ?? null;

  // Fetch the full bio for the currently visible plant so PlantBioView can
  // render the same layout as the detail page. Background polling is disabled
  // — a plant only stays on screen for DISPLAY_DURATION_MS.
  const { data: detail } = useQuery({
    queryKey: ["plant", currentPlantId],
    queryFn: () => getPlant(currentPlantId as number),
    enabled: currentPlantId != null,
    staleTime: 60_000,
  });

  useEffect(() => {
    let enteredFullscreen = false;
    const el = document.documentElement;
    el.requestFullscreen?.()
      .then(() => {
        enteredFullscreen = true;
      })
      .catch(() => {});

    // Escape in fullscreen is consumed by the browser to exit fullscreen, so
    // our keydown listener never sees it. Treat the fullscreen exit itself as
    // the signal to leave the screensaver.
    const onFsChange = () => {
      if (enteredFullscreen && document.fullscreenElement == null) {
        router.push("/plants");
      }
    };
    document.addEventListener("fullscreenchange", onFsChange);

    return () => {
      document.removeEventListener("fullscreenchange", onFsChange);
      if (document.fullscreenElement) {
        document.exitFullscreen?.().catch(() => {});
      }
    };
  }, [router]);

  // Prevent the display from dimming / sleeping while the screensaver is
  // running. The Screen Wake Lock is auto-released when the tab becomes
  // hidden, so re-acquire it whenever visibility returns.
  useEffect(() => {
    if (typeof navigator === "undefined" || !("wakeLock" in navigator)) return;
    let sentinel: WakeLockSentinel | null = null;
    let cancelled = false;

    const acquire = async () => {
      try {
        const lock = await navigator.wakeLock.request("screen");
        if (cancelled) {
          lock.release().catch(() => {});
          return;
        }
        sentinel = lock;
        lock.addEventListener("release", () => {
          if (sentinel === lock) sentinel = null;
        });
      } catch {
        // Best-effort: ignore unsupported / denied requests.
      }
    };

    const onVisibility = () => {
      if (document.visibilityState === "visible" && sentinel == null) {
        void acquire();
      }
    };

    void acquire();
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      cancelled = true;
      document.removeEventListener("visibilitychange", onVisibility);
      sentinel?.release().catch(() => {});
      sentinel = null;
    };
  }, []);

  useEffect(() => {
    if (queue.length <= 1) return;
    const timer = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % queue.length);
    }, DISPLAY_DURATION_MS);
    return () => clearInterval(timer);
  }, [queue]);

  const handleExit = useCallback(() => {
    if (document.fullscreenElement) {
      document.exitFullscreen?.().catch(() => {});
    }
    router.push("/plants");
  }, [router]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") handleExit();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [handleExit]);

  const detailMatchesCurrent = detail?.id === currentPlantId;

  // Re-roll the secondary image each time a plant is selected for a slide.
  // Pool = history photos + real reference images, excluding the hero so the
  // two panels never show the exact same picture. Text/html reference entries
  // are iNat/GBIF link tiles, not actual images.
  const secondaryImage = useMemo<PlantImageDto | null>(() => {
    if (!detail || !detailMatchesCurrent) return null;
    const hero = detail.illustratedImage ?? detail.originalImages[0] ?? null;
    const history = [...detail.originalImages, ...detail.pruneUpdateImages];
    const reference = detail.healthyReferenceImages.filter(
      (img) => img.mimeType !== "text/html"
    );
    const pool = [...history, ...reference].filter(
      (img) => plantImageSrc(img.url) !== plantImageSrc(hero?.url ?? "")
    );
    if (pool.length === 0) return null;
    return pool[Math.floor(Math.random() * pool.length)];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentIndex, detail?.id, detailMatchesCurrent]);

  // If the current plant has no usable secondary image (no history and no
  // reference photos), skip forward so the screensaver never shows a blank
  // second panel.
  useEffect(() => {
    if (!detail || !detailMatchesCurrent) return;
    if (secondaryImage == null && queue.length > 1) {
      setCurrentIndex((i) => (i + 1) % queue.length);
    }
  }, [detail, detailMatchesCurrent, secondaryImage, queue.length]);

  return (
    <div className="fixed inset-0 bg-stone-50 overflow-hidden">
      <div className="absolute top-0 left-0 right-0 h-0.5 bg-stone-200/60 z-40">
        <div
          key={currentIndex}
          className="h-full bg-stone-400/70"
          style={{
            animation: `progress ${DISPLAY_DURATION_MS}ms linear forwards`,
          }}
        />
      </div>

      {current && detail && detailMatchesCurrent ? (
        <main className="h-full w-full flex flex-col px-3 py-2">
          <PlantBioView
            plant={detail}
            readOnly
            screensaverAuxImage={secondaryImage}
          />
        </main>
      ) : (
        <div className="flex h-full items-center justify-center text-sm text-stone-400 italic">
          {queue.length === 0 ? "Loading your plants…" : "Loading plant bio…"}
        </div>
      )}

      <style>{`
        @keyframes progress {
          from { width: 0% }
          to { width: 100% }
        }
      `}</style>
    </div>
  );
}
