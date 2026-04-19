import { describe, expect, it } from "vitest";
import type { ReminderStateDto } from "@/types/plant";
import { computeIconState, MUTED_ICON_CLASS } from "./reminderIconState";

function makeState(overrides: Partial<ReminderStateDto> = {}): ReminderStateDto {
  return {
    wateringDue: false,
    wateringOverdue: false,
    fertilizerDue: false,
    pruningDue: false,
    healthAttentionNeeded: false,
    lightAttentionNeeded: false,
    placementAttentionNeeded: false,
    nextWateringInstruction: null,
    nextFertilizerInstruction: null,
    nextPruningInstruction: null,
    healthAttentionReason: null,
    lightAttentionReason: null,
    placementAttentionReason: null,
    weatherCareNote: null,
    lastComputedAt: "2026-04-19T00:00:00Z",
    ...overrides,
  };
}

describe("computeIconState: water", () => {
  it("muted by default", () => {
    const s = computeIconState("water", makeState());
    expect(s.colorClass).toBe(MUTED_ICON_CLASS);
    expect(s.active).toBe(false);
    expect(s.label).toBe("Watering not due");
  });

  it("due uses lighter sky", () => {
    const s = computeIconState("water", makeState({ wateringDue: true }));
    expect(s.colorClass).toBe("text-sky-500");
    expect(s.active).toBe(true);
    expect(s.label).toBe("Watering due");
  });

  it("overdue wins over due and uses darker sky", () => {
    const s = computeIconState(
      "water",
      makeState({ wateringDue: true, wateringOverdue: true })
    );
    expect(s.colorClass).toBe("text-sky-600");
    expect(s.active).toBe(true);
    expect(s.label).toBe("Watering overdue");
  });
});

describe("computeIconState: fertilizer + pruning", () => {
  it("fertilizer active toggles color and label", () => {
    expect(computeIconState("fertilizer", makeState()).active).toBe(false);
    const due = computeIconState("fertilizer", makeState({ fertilizerDue: true }));
    expect(due.colorClass).toBe("text-amber-600");
    expect(due.active).toBe(true);
    expect(due.label).toBe("Fertilizer due");
  });

  it("pruning active toggles color and label", () => {
    expect(computeIconState("pruning", makeState()).active).toBe(false);
    const due = computeIconState("pruning", makeState({ pruningDue: true }));
    expect(due.colorClass).toBe("text-green-600");
    expect(due.active).toBe(true);
    expect(due.label).toBe("Pruning due");
  });
});

describe("computeIconState: light", () => {
  it("muted + calm label when not flagged", () => {
    const s = computeIconState("light", makeState());
    expect(s.colorClass).toBe(MUTED_ICON_CLASS);
    expect(s.active).toBe(false);
    expect(s.label).toBe("Light looks fine");
  });

  it("illuminates with bio-section reason when flagged", () => {
    const s = computeIconState(
      "light",
      makeState({
        lightAttentionNeeded: true,
        lightAttentionReason: "Likely too dark for this species",
      })
    );
    expect(s.colorClass).toBe("text-amber-500");
    expect(s.active).toBe(true);
    expect(s.label).toBe("Likely too dark for this species");
  });

  it("falls back to generic label when reason string is missing", () => {
    const s = computeIconState(
      "light",
      makeState({ lightAttentionNeeded: true, lightAttentionReason: null })
    );
    expect(s.colorClass).toBe("text-amber-500");
    expect(s.active).toBe(true);
    expect(s.label).toBe("Light needs attention");
  });
});

describe("computeIconState: placement", () => {
  it("muted + calm label when not flagged", () => {
    const s = computeIconState("placement", makeState());
    expect(s.colorClass).toBe(MUTED_ICON_CLASS);
    expect(s.active).toBe(false);
    expect(s.label).toBe("Placement looks fine");
  });

  it("illuminates with bio-section reason when flagged", () => {
    const s = computeIconState(
      "placement",
      makeState({
        placementAttentionNeeded: true,
        placementAttentionReason: "Likely outgrowing its pot",
      })
    );
    expect(s.colorClass).toBe("text-emerald-600");
    expect(s.active).toBe(true);
    expect(s.label).toBe("Likely outgrowing its pot");
  });
});

describe("computeIconState: health", () => {
  it("muted + calm label when not flagged", () => {
    const s = computeIconState("health", makeState());
    expect(s.colorClass).toBe(MUTED_ICON_CLASS);
    expect(s.active).toBe(false);
    expect(s.label).toBe("Health looks fine");
  });

  it("illuminates with bio-section reason when flagged", () => {
    const s = computeIconState(
      "health",
      makeState({
        healthAttentionNeeded: true,
        healthAttentionReason: "Yellowing lower leaves, likely overwatering",
      })
    );
    expect(s.colorClass).toBe("text-rose-500");
    expect(s.active).toBe(true);
    expect(s.label).toBe("Yellowing lower leaves, likely overwatering");
  });
});

describe("computeIconState: state matrix over the three new flags", () => {
  // Pins every on/off combination of light/placement/health so future changes
  // to one branch can't silently drop another.
  const combos: Array<[boolean, boolean, boolean]> = [
    [false, false, false],
    [false, false, true],
    [false, true, false],
    [false, true, true],
    [true, false, false],
    [true, false, true],
    [true, true, false],
    [true, true, true],
  ];

  combos.forEach(([light, placement, health]) => {
    it(`light=${light} placement=${placement} health=${health}`, () => {
      const state = makeState({
        lightAttentionNeeded: light,
        placementAttentionNeeded: placement,
        healthAttentionNeeded: health,
      });
      expect(computeIconState("light", state).active).toBe(light);
      expect(computeIconState("placement", state).active).toBe(placement);
      expect(computeIconState("health", state).active).toBe(health);
    });
  });
});
