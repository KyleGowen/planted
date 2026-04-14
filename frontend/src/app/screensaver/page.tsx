"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import Image from "next/image";
import { Sprout, X } from "lucide-react";
import { listPlants } from "@/lib/api";
import type { PlantListItemResponse } from "@/types/plant";

const DISPLAY_DURATION_MS = 30_000;

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
  const [queue, setQueue] = useState<PlantListItemResponse[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isFullscreen, setIsFullscreen] = useState(false);

  const { data: plants } = useQuery({
    queryKey: ["plants"],
    queryFn: listPlants,
    staleTime: 60_000,
  });

  // Build randomized queue when plants load
  useEffect(() => {
    if (plants && plants.length > 0) {
      setQueue(shuffle(plants));
      setCurrentIndex(0);
    }
  }, [plants]);

  // Enter fullscreen on mount
  useEffect(() => {
    const el = document.documentElement;
    el.requestFullscreen?.().then(() => setIsFullscreen(true)).catch(() => {});
    return () => {
      if (document.fullscreenElement) {
        document.exitFullscreen?.().catch(() => {});
      }
    };
  }, []);

  // Cycle every 30 seconds
  useEffect(() => {
    if (queue.length === 0) return;
    const timer = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % queue.length);
    }, DISPLAY_DURATION_MS);
    return () => clearInterval(timer);
  }, [queue]);

  // Escape exits
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

  const current = queue[currentIndex];

  return (
    <div className="fixed inset-0 bg-stone-950 text-white overflow-hidden">
      {/* Exit button */}
      <button
        onClick={handleExit}
        className="absolute top-4 right-4 z-50 p-2 rounded-full bg-white/10 hover:bg-white/20 transition-colors"
        aria-label="Exit screensaver"
      >
        <X size={18} />
      </button>

      {/* Progress bar */}
      <div className="absolute top-0 left-0 right-0 h-0.5 bg-white/10 z-40">
        <div
          key={currentIndex}
          className="h-full bg-white/30"
          style={{
            animation: `progress ${DISPLAY_DURATION_MS}ms linear forwards`,
          }}
        />
      </div>

      {/* Plant display */}
      {current ? (
        <div className="flex h-full items-center justify-center">
          {/* Background image (blurred) */}
          {current.illustratedImage && (
            <div className="absolute inset-0">
              <Image
                src={current.illustratedImage.url}
                alt=""
                fill
                className="object-cover opacity-20 blur-xl scale-110"
              />
            </div>
          )}

          {/* Main content */}
          <div className="relative z-10 flex flex-col items-center gap-6 px-8 text-center max-w-lg">
            {/* Illustrated image */}
            <div className="relative h-64 w-64 rounded-2xl overflow-hidden bg-white/5">
              {current.illustratedImage ? (
                <Image
                  src={current.illustratedImage.url}
                  alt={current.displayLabel}
                  fill
                  className="object-cover"
                />
              ) : (
                <div className="flex h-full w-full items-center justify-center">
                  <Sprout size={48} className="text-white/20" />
                </div>
              )}
            </div>

            {/* Plant identity */}
            <div>
              {current.name ? (
                <>
                  <h2 className="text-3xl font-semibold tracking-tight plant-name text-white">
                    {current.name}
                  </h2>
                  {(current.genus || current.species) && (
                    <p className="text-white/50 italic mt-1 species-label">
                      {[current.genus, current.species].filter(Boolean).join(" ")}
                    </p>
                  )}
                </>
              ) : (
                <h2 className="text-3xl font-semibold italic tracking-tight plant-name species-label text-white">
                  {current.speciesLabel ?? "Unknown plant"}
                </h2>
              )}
            </div>

            {/* Plant counter */}
            <div className="flex gap-1.5">
              {queue.map((_, i) => (
                <span
                  key={i}
                  className={`h-1 rounded-full transition-all ${
                    i === currentIndex ? "w-4 bg-white/70" : "w-1 bg-white/20"
                  }`}
                />
              ))}
            </div>
          </div>
        </div>
      ) : (
        <div className="flex h-full items-center justify-center text-white/30 text-sm">
          Loading your plants…
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
