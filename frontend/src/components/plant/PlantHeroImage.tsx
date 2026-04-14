"use client";

import Image from "next/image";
import { Skeleton } from "@/components/ui/skeleton";
import { Sprout } from "lucide-react";
import { cn } from "@/lib/utils";
import type { PlantImageDto } from "@/types/plant";

interface Props {
  image: PlantImageDto | null;
  isLoading?: boolean;
  className?: string;
}

export function PlantHeroImage({ image, isLoading, className }: Props) {
  if (isLoading) {
    return <Skeleton className={cn("rounded-xl", className ?? "aspect-square w-full")} />;
  }

  if (!image) {
    return (
      <div
        className={cn(
          "flex flex-col items-center justify-center rounded-xl bg-stone-100 text-stone-400",
          className ?? "aspect-square w-full"
        )}
      >
        <Sprout size={40} className="opacity-40" />
        <p className="mt-2 text-xs">Illustration pending</p>
      </div>
    );
  }

  return (
    <div className={cn("relative overflow-hidden rounded-xl bg-stone-100", className ?? "aspect-square w-full")}>
      <Image
        src={image.url}
        alt="Plant"
        fill
        className="object-cover transition-opacity duration-300"
        sizes="(max-width: 768px) 100vw, 50vw"
        priority
      />
    </div>
  );
}
