"use client";

import { Droplets, Leaf, Scissors, AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ReminderStateDto } from "@/types/plant";

interface Props {
  reminderState: ReminderStateDto | null;
  className?: string;
  size?: number;
}

export function ReminderIconRow({ reminderState, className, size = 16 }: Props) {
  if (!reminderState) return null;

  const { wateringDue, wateringOverdue, fertilizerDue, pruningDue, healthAttentionNeeded, goalAttentionNeeded } =
    reminderState;

  return (
    <div className={cn("flex items-center gap-2", className)}>
      <Droplets
        size={size}
        className={cn(
          "transition-colors",
          wateringOverdue
            ? "text-sky-600"
            : wateringDue
            ? "text-sky-500"
            : "text-stone-300"
        )}
        aria-label={wateringDue ? "Watering due" : "Watering not due"}
      />
      <Leaf
        size={size}
        className={cn(
          "transition-colors",
          fertilizerDue ? "text-amber-600" : "text-stone-300"
        )}
        aria-label={fertilizerDue ? "Fertilizer due" : "Fertilizer not due"}
      />
      <Scissors
        size={size}
        className={cn(
          "transition-colors",
          pruningDue ? "text-green-600" : "text-stone-300"
        )}
        aria-label={pruningDue ? "Pruning due" : "Pruning not due"}
      />
      {(healthAttentionNeeded || goalAttentionNeeded) && (
        <AlertTriangle
          size={size}
          className="text-amber-500"
          aria-label="Needs attention"
        />
      )}
    </div>
  );
}
