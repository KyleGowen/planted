"use client";

import { Droplets, Leaf, Scissors, Sun, MapPin, HeartPulse } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ReminderStateDto } from "@/types/plant";
import { computeIconState } from "./reminderIconState";

interface Props {
  reminderState: ReminderStateDto | null;
  className?: string;
  size?: number;
}

/**
 * Shared care status indicator. Renders six fixed icons — Water, Fertilizer,
 * Pruning, Light, Placement, Health — so every plant in the list and screensaver
 * shows the same stable layout. Each icon illuminates (colored) only when the
 * corresponding attention flag on {@link ReminderStateDto} is true; otherwise it
 * is muted. The qualitative light/placement/health flags come from the bio-section
 * LLM prompts (see {@code plant_light_care_v1 / plant_placement_care_v1 /
 * plant_health_assessment_v1}) and are synced onto {@code plant_reminder_state}
 * by {@code PlantReminderService.syncBioAttention} when those bio sections
 * complete. Per-icon color + aria-label decisions live in {@code computeIconState}.
 */
export function ReminderIconRow({ reminderState, className, size = 16 }: Props) {
  if (!reminderState) return null;

  const water = computeIconState("water", reminderState);
  const fertilizer = computeIconState("fertilizer", reminderState);
  const pruning = computeIconState("pruning", reminderState);
  const light = computeIconState("light", reminderState);
  const placement = computeIconState("placement", reminderState);
  const health = computeIconState("health", reminderState);

  return (
    <div className={cn("flex items-center gap-2", className)}>
      <Droplets
        size={size}
        className={cn("transition-colors", water.colorClass)}
        aria-label={water.label}
      />
      <Leaf
        size={size}
        className={cn("transition-colors", fertilizer.colorClass)}
        aria-label={fertilizer.label}
      />
      <Scissors
        size={size}
        className={cn("transition-colors", pruning.colorClass)}
        aria-label={pruning.label}
      />
      <Sun
        size={size}
        className={cn("transition-colors", light.colorClass)}
        aria-label={light.label}
      />
      <MapPin
        size={size}
        className={cn("transition-colors", placement.colorClass)}
        aria-label={placement.label}
      />
      <HeartPulse
        size={size}
        className={cn("transition-colors", health.colorClass)}
        aria-label={health.label}
      />
    </div>
  );
}
