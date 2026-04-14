"use client";

import { Globe, BookOpen, ScrollText, Utensils } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { SpeciesOverviewProse } from "@/components/plant/SpeciesOverviewProse";
import type { AnalysisSummaryDto } from "@/types/plant";

interface Props {
  analysis: AnalysisSummaryDto | null;
}

export function FunFactsPanel({ analysis }: Props) {
  if (!analysis || analysis.status !== "COMPLETED") {
    return (
      <Card className="border-stone-200">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium text-stone-500">About This Plant</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-stone-400 italic">Species profile is being prepared…</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="border-stone-200">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium text-stone-600">About This Plant</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {analysis.nativeRegions && analysis.nativeRegions.length > 0 && (
          <FactRow
            icon={<Globe size={15} className="text-stone-400" />}
            label="Native to"
            value={analysis.nativeRegions.join(", ")}
          />
        )}

        {analysis.speciesOverview && analysis.speciesOverview.trim().length > 0 && (
          <div>
            <div className="flex items-center gap-1.5 mb-2">
              <ScrollText size={13} className="text-stone-400" />
              <span className="text-xs font-medium uppercase tracking-wide text-stone-400">
                Species overview
              </span>
            </div>
            <SpeciesOverviewProse text={analysis.speciesOverview} />
          </div>
        )}

        {analysis.uses && analysis.uses.length > 0 && (
          <div>
            <div className="flex items-center gap-1.5 mb-2">
              <Utensils size={13} className="text-stone-400" />
              <span className="text-xs font-medium uppercase tracking-wide text-stone-400">Uses</span>
            </div>
            <ul className="space-y-1">
              {analysis.uses.map((use, i) => (
                <li key={i} className="text-sm text-stone-600 flex gap-2">
                  <span className="text-stone-300 flex-shrink-0">–</span>
                  <span>{use}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {analysis.propagationInstructions && (
          <FactRow
            icon={<BookOpen size={15} className="text-stone-400" />}
            label="Propagation"
            value={analysis.propagationInstructions}
          />
        )}
      </CardContent>
    </Card>
  );
}

function FactRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex gap-2 text-sm text-stone-600">
      <span className="mt-0.5 flex-shrink-0">{icon}</span>
      <div>
        <span className="text-xs font-medium uppercase tracking-wide text-stone-400 block mb-0.5">
          {label}
        </span>
        <span>{value}</span>
      </div>
    </div>
  );
}
