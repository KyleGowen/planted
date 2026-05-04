"use client";

import Image from "next/image";
import { cn } from "@/lib/utils";
import { plantImageSrc } from "@/lib/plantMediaUrl";
import type { PlantImageDto } from "@/types/plant";

interface Props {
  images: PlantImageDto[];
  activeImageId?: number | null;
  onSelect?: (image: PlantImageDto) => void;
  className?: string;
}

export function PlantImageStrip({ images, activeImageId, onSelect, className }: Props) {
  if (!images.length) return null;

  return (
    <div className={cn("image-strip", className)}>
      {images.map((img) => (
        <button
          key={img.id}
          onClick={() => onSelect?.(img)}
          className={cn(
            "relative flex-shrink-0 h-16 w-16 rounded-lg overflow-hidden border transition-all",
            activeImageId === img.id
              ? "border-stone-500 ring-1 ring-stone-400"
              : "border-stone-200 hover:border-stone-300"
          )}
          aria-label={`View image ${img.id}`}
        >
          {(() => {
            const src = plantImageSrc(img.url);
            return (src.startsWith("/") ||
            (src.startsWith("http") &&
              !src.includes("inaturalist") &&
              !src.includes("gbif"))) ? (
            <Image
              src={src}
              alt=""
              fill
              className="object-cover"
              sizes="64px"
            />
          ) : (
            <div className="flex h-full w-full items-center justify-center bg-stone-100 text-xs text-stone-400">
              ref
            </div>
          );
          })()}
        </button>
      ))}
    </div>
  );
}
