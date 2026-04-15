import type { PlantHistoryEntryDto } from "@/types/plant";

/** LLM history-summary lines: "April 13, 2026 at 19:08: body" — must be one line (see ASI / regexp literal rules). */
export const SUMMARY_DATED_LINE = /^((?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},\s+\d{4}\s+at\s+(?:\d{1,2}:\d{2}(?::\d{2})?|\d{1,2}:\d{2}\s*[AP]M))\s*:\s*(.*)$/i;

/** Local calendar day key for grouping (YYYY-MM-DD). */
export function dayKeyLocal(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function formatDayTileTitle(dayKey: string, locale?: string): string {
  const [ys, ms, ds] = dayKey.split("-").map(Number);
  const d = new Date(ys, ms - 1, ds);
  return d.toLocaleDateString(locale, {
    weekday: "long",
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}

export type TimelineRow =
  | { kind: "entry"; at: Date; entry: PlantHistoryEntryDto }
  | { kind: "summary"; at: Date; text: string };

export type HistoryDayTile = {
  dayKey: string;
  rows: TimelineRow[];
};

/**
 * Best-effort: walks lines of the LLM history summary; each line that starts with a dated
 * header becomes a segment; continuation lines (no new date) append to the previous segment.
 * Leading lines with no date become `remainder`.
 */
export function parseHistorySummarySegments(raw: string): {
  datedSegments: { at: Date; body: string }[];
  remainder: string;
} {
  const text = raw.trim();
  if (!text) {
    return { datedSegments: [], remainder: "" };
  }

  const lines = text.split(/\r?\n/);
  const datedSegments: { at: Date; body: string }[] = [];
  const preamble: string[] = [];
  let current: { header: string; bodyLines: string[] } | null = null;

  function flushCurrent() {
    if (!current) return;
    const header = current.header.trim();
    // V8 Date.parse rejects "... at HH:MM" — normalize to "... HH:MM" / "... H:MM AM"
    const forParse = header.replace(/\s+at\s+/i, " ");
    const t = Date.parse(forParse);
    const body = current.bodyLines.join("\n").trim();
    if (!Number.isNaN(t)) {
      datedSegments.push({ at: new Date(t), body });
    } else if (body) {
      preamble.push(`${header}: ${body}`);
    }
    current = null;
  }

  for (const line of lines) {
    const m = line.trim().match(/^((?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},\s+\d{4}\s+at\s+(?:\d{1,2}:\d{2}(?::\d{2})?|\d{1,2}:\d{2}\s*[AP]M))\s*:\s*(.*)$/i);
    if (m) {
      flushCurrent();
      current = { header: m[1], bodyLines: [m[2]] };
      continue;
    }
    if (current) {
      current.bodyLines.push(line);
    } else {
      preamble.push(line);
    }
  }
  flushCurrent();

  const remainder = preamble.join("\n").trim();
  return { datedSegments, remainder };
}

/**
 * Non-dated prose left after splitting (or the whole summary if nothing matched).
 * Placed on the local calendar day of `summaryCompletedAt` when possible.
 */
function fallbackSummaryRow(
  remainder: string,
  summaryCompletedAtIso: string | null
): { at: Date; text: string } | null {
  const prose = remainder.trim();
  if (!prose) return null;
  if (summaryCompletedAtIso) {
    const d = new Date(summaryCompletedAtIso);
    if (!Number.isNaN(d.getTime())) {
      return { at: startOfLocalDay(d), text: prose };
    }
  }
  return { at: new Date(0), text: prose };
}

function startOfLocalDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0, 0);
}

/**
 * Merges structured journal/care rows with parsed summary segments; groups by local day;
 * sorts days newest-first; sorts rows within each day by time descending.
 */
export function buildHistoryDayTiles(
  entries: PlantHistoryEntryDto[],
  summaryText: string | null,
  summaryCompletedAtIso: string | null
): HistoryDayTile[] {
  const rows: TimelineRow[] = [];

  for (const entry of entries) {
    const at = new Date(entry.createdAt);
    if (!Number.isNaN(at.getTime())) {
      rows.push({ kind: "entry", at, entry });
    }
  }

  const trimmedSummary = summaryText?.trim() ?? "";
  if (trimmedSummary) {
    const { datedSegments, remainder } = parseHistorySummarySegments(trimmedSummary);
    for (const seg of datedSegments) {
      rows.push({ kind: "summary", at: seg.at, text: seg.body });
    }
    const fb = fallbackSummaryRow(remainder, summaryCompletedAtIso);
    if (fb) {
      rows.push({ kind: "summary", at: fb.at, text: fb.text });
    }
  }

  const byDay = new Map<string, TimelineRow[]>();
  for (const row of rows) {
    const key = dayKeyLocal(row.at);
    const list = byDay.get(key) ?? [];
    list.push(row);
    byDay.set(key, list);
  }

  for (const [, list] of byDay) {
    list.sort((a, b) => b.at.getTime() - a.at.getTime());
  }

  const dayKeys = [...byDay.keys()].sort((a, b) => {
    const maxA = Math.max(...(byDay.get(a) ?? []).map((r) => r.at.getTime()));
    const maxB = Math.max(...(byDay.get(b) ?? []).map((r) => r.at.getTime()));
    return maxB - maxA;
  });

  return dayKeys.map((dayKey) => ({
    dayKey,
    rows: byDay.get(dayKey) ?? [],
  }));
}
