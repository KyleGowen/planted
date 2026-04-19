import type { ReminderStateDto } from "@/types/plant";

/**
 * Pure helpers that decide which of the six care icons in {@code ReminderIconRow}
 * should illuminate (colored vs muted) and what aria-label / tooltip each icon
 * should carry. Extracted from the component so the rules are easy to unit-test
 * without a DOM; the component itself just composes these helpers with Lucide icons.
 */

export const MUTED_ICON_CLASS = "text-stone-300";

const WATER_OVERDUE_CLASS = "text-sky-600";
const WATER_DUE_CLASS = "text-sky-500";
const FERTILIZER_DUE_CLASS = "text-amber-600";
const PRUNING_DUE_CLASS = "text-green-600";
const LIGHT_ATTENTION_CLASS = "text-amber-500";
const PLACEMENT_ATTENTION_CLASS = "text-emerald-600";
const HEALTH_ATTENTION_CLASS = "text-rose-500";

export type ReminderIconId =
  | "water"
  | "fertilizer"
  | "pruning"
  | "light"
  | "placement"
  | "health";

export interface IconState {
  colorClass: string;
  label: string;
  active: boolean;
}

/**
 * Compute the color class + aria-label for one of the six care icons. Order
 * matches the visual order in {@code ReminderIconRow}; callers should render
 * every icon so the row layout stays stable across plants.
 */
export function computeIconState(
  id: ReminderIconId,
  state: ReminderStateDto
): IconState {
  switch (id) {
    case "water": {
      if (state.wateringOverdue) {
        return { colorClass: WATER_OVERDUE_CLASS, label: "Watering overdue", active: true };
      }
      if (state.wateringDue) {
        return { colorClass: WATER_DUE_CLASS, label: "Watering due", active: true };
      }
      return { colorClass: MUTED_ICON_CLASS, label: "Watering not due", active: false };
    }
    case "fertilizer": {
      if (state.fertilizerDue) {
        return { colorClass: FERTILIZER_DUE_CLASS, label: "Fertilizer due", active: true };
      }
      return { colorClass: MUTED_ICON_CLASS, label: "Fertilizer not due", active: false };
    }
    case "pruning": {
      if (state.pruningDue) {
        return { colorClass: PRUNING_DUE_CLASS, label: "Pruning due", active: true };
      }
      return { colorClass: MUTED_ICON_CLASS, label: "Pruning not due", active: false };
    }
    case "light": {
      if (state.lightAttentionNeeded) {
        return {
          colorClass: LIGHT_ATTENTION_CLASS,
          label: state.lightAttentionReason || "Light needs attention",
          active: true,
        };
      }
      return { colorClass: MUTED_ICON_CLASS, label: "Light looks fine", active: false };
    }
    case "placement": {
      if (state.placementAttentionNeeded) {
        return {
          colorClass: PLACEMENT_ATTENTION_CLASS,
          label: state.placementAttentionReason || "Placement needs attention",
          active: true,
        };
      }
      return { colorClass: MUTED_ICON_CLASS, label: "Placement looks fine", active: false };
    }
    case "health": {
      if (state.healthAttentionNeeded) {
        return {
          colorClass: HEALTH_ATTENTION_CLASS,
          label: state.healthAttentionReason || "Health needs attention",
          active: true,
        };
      }
      return { colorClass: MUTED_ICON_CLASS, label: "Health looks fine", active: false };
    }
  }
}
