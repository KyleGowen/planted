import { describe, expect, it } from "vitest";
import {
  buildHistoryDayTiles,
  parseHistorySummarySegments,
  dayKeyLocal,
  SUMMARY_DATED_LINE,
} from "./historyDayTimeline";

describe("parseHistorySummarySegments", () => {
  it("dated-line regex matches a typical summary line", () => {
    const datedLine = /^((?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},\s+\d{4}\s+at\s+(?:\d{1,2}:\d{2}(?::\d{2})?|\d{1,2}:\d{2}\s*[AP]M))\s*:\s*(.*)$/i;
    expect("April 13, 2026 at 19:08: [Photo]".trim().match(datedLine)).not.toBeNull();
    expect(SUMMARY_DATED_LINE.source).toBe(datedLine.source);
    expect("April 13, 2026 at 19:08: [Photo]".trim().match(SUMMARY_DATED_LINE)).not.toBeNull();
  });

  it("parses a single dated summary line via exported parser", () => {
    const line = "April 13, 2026 at 19:08: hello";
    expect(line.trim().match(SUMMARY_DATED_LINE)).not.toBeNull();
    const { datedSegments, remainder } = parseHistorySummarySegments(line);
    expect(remainder).toBe("");
    expect(datedSegments).toHaveLength(1);
    expect(datedSegments[0].body).toBe("hello");
  });

  it("parses multiple dated lines like the LLM history summary", () => {
    const raw = `April 13, 2026 at 19:08: [Photo attached] shows a healthy plant except for the dead branch.

April 13, 2026 at 19:01: Noted that the branch on the right is dead.

April 13, 2026 at 19:00: [Photo attached] shows a branch that appears to be dead.`;

    const { datedSegments, remainder } = parseHistorySummarySegments(raw);
    expect(remainder).toBe("");
    expect(datedSegments).toHaveLength(3);
    expect(datedSegments[0].body).toContain("healthy plant");
    expect(dayKeyLocal(datedSegments[0].at)).toBe(dayKeyLocal(datedSegments[1].at));
  });

  it("puts non-matching preamble in remainder only", () => {
    const raw = "Intro without a date.\n\nApril 14, 2026 at 12:00: Something happened.";
    const { datedSegments, remainder } = parseHistorySummarySegments(raw);
    expect(datedSegments).toHaveLength(1);
    expect(remainder).toContain("Intro without");
  });
});

describe("buildHistoryDayTiles", () => {
  it("places summary-only dated lines in one tile per calendar day", () => {
    const summary = `April 13, 2026 at 19:08: First.

April 13, 2026 at 19:00: Second.`;

    const tiles = buildHistoryDayTiles([], summary, "2026-04-13T12:00:00.000Z");
    expect(tiles).toHaveLength(1);
    expect(tiles[0].rows).toHaveLength(2);
    expect(tiles[0].rows.every((r) => r.kind === "summary")).toBe(true);
  });

  it("includes structured entries in tiles", () => {
    const entries = [
      {
        id: 1,
        entryKind: "WATERING",
        noteText: "Watered",
        image: null,
        createdAt: "2026-04-14T12:00:00.000Z",
      },
    ];

    const tiles = buildHistoryDayTiles(entries, null, null);
    expect(tiles.length).toBeGreaterThanOrEqual(1);
    expect(tiles.some((t) => t.rows.some((r) => r.kind === "entry"))).toBe(true);
  });
});
