"use client";

import { Droplets, Leaf, Scissors, Sun, MapPin, HeartPulse } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ReminderStateDto } from "@/types/plant";
import { computeIconState } from "./reminderIconState";

interface Props {
  reminderState: ReminderStateDto | null;
  className?: string;
  size?: number;
  /** When provided, the Water icon becomes a tappable button that calls this handler. */
  onWater?: () => void;
  /** When provided, the Fertilizer icon becomes a tappable button that calls this handler. */
  onFertilizer?: () => void;
  /** When provided, the Prune icon becomes a tappable button that calls this handler. */
  onPrune?: () => void;
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
 *
 * The optional {@code onWater}, {@code onFertilizer}, and {@code onPrune} props
 * make those three icons interactive (e.g. in screensaver mode). Light, Placement,
 * and Health are always informational only.
 */
export function ReminderIconRow({
  reminderState,
  className,
  size = 16,
  onWater,
  onFertilizer,
  onPrune,
}: Props) {
  if (!reminderState) return null;

  const water = computeIconState("water", reminderState);
  const fertilizer = computeIconState("fertilizer", reminderState);
  const pruning = computeIconState("pruning", reminderState);
  const light = computeIconState("light", reminderState);
  const placement = computeIconState("placement", reminderState);
  const health = computeIconState("health", reminderState);

  const actionableIconClass =
    "cursor-pointer transition-opacity hover:opacity-70 active:scale-90 transition-transform focus:outline-none";

  return (
    <div className={cn("flex items-center gap-2", className)}>
      {onWater ? (
        <button onClick={onWater} className={actionableIconClass} aria-label={water.label}>
          <Droplets size={size} className={cn("transition-colors", water.colorClass)} />
        </button>
      ) : (
        <Droplets
          size={size}
          className={cn("transition-colors", water.colorClass)}
          aria-label={water.label}
        />
      )}
      {onFertilizer ? (
        <button onClick={onFertilizer} className={actionableIconClass} aria-label={fertilizer.label}>
          <Leaf size={size} className={cn("transition-colors", fertilizer.colorClass)} />
        </button>
      ) : (
        <Leaf
          size={size}
          className={cn("transition-colors", fertilizer.colorClass)}
          aria-label={fertilizer.label}
        />
      )}
      {onPrune ? (
        <button onClick={onPrune} className={actionableIconClass} aria-label={pruning.label}>
          <Scissors size={size} className={cn("transition-colors", pruning.colorClass)} />
        </button>
      ) : (
        <Scissors
          size={size}
          className={cn("transition-colors", pruning.colorClass)}
          aria-label={pruning.label}
        />
      )}
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
