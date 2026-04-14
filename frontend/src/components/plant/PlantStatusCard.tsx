"use client";

import { cn } from "@/lib/utils";
import type { AnalysisStatus } from "@/types/plant";

interface Props {
  status: AnalysisStatus | null;
  className?: string;
}

const STATUS_CONFIG: Record<
  AnalysisStatus,
  { label: string; description: string; className: string }
> = {
  PENDING: {
    label: "Analyzing",
    description: "Your plant is being identified. This usually takes a moment.",
    className: "bg-stone-50 border-stone-200 text-stone-600",
  },
  PROCESSING: {
    label: "Processing",
    description: "Building your plant's care profile…",
    className: "bg-stone-50 border-stone-200 text-stone-600",
  },
  COMPLETED: {
    label: "Analysis complete",
    description: "Your plant's profile is ready.",
    className: "bg-green-50 border-green-100 text-green-700",
  },
  FAILED: {
    label: "Analysis unavailable",
    description: "We couldn't complete the analysis. You can still track this plant.",
    className: "bg-amber-50 border-amber-100 text-amber-700",
  },
};

export function PlantStatusCard({ status, className }: Props) {
  if (!status || status === "COMPLETED") return null;

  const config = STATUS_CONFIG[status];

  return (
    <div
      className={cn(
        "rounded-lg border px-4 py-3 text-sm",
        config.className,
        className
      )}
      role="status"
    >
      <div className="flex items-center gap-2">
        {(status === "PENDING" || status === "PROCESSING") && (
          <span className="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-stone-400" />
        )}
        <span className="font-medium">{config.label}</span>
      </div>
      <p className="mt-0.5 text-xs opacity-80">{config.description}</p>
    </div>
  );
}
