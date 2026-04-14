"use client";

import { Droplets, Leaf, Scissors, Sun, MapPin } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { AnalysisSummaryDto, ReminderStateDto } from "@/types/plant";

interface Props {
  analysis: AnalysisSummaryDto | null;
  reminderState: ReminderStateDto | null;
}

export function CareInstructionPanel({ analysis, reminderState }: Props) {
  if (!analysis || analysis.status !== "COMPLETED") {
    return (
      <Card className="border-stone-200">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium text-stone-500">Care Instructions</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-stone-400 italic">
            Care profile is being prepared…
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="border-stone-200">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium text-stone-600">Care Instructions</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {/* Active reminders first */}
        {reminderState?.nextWateringInstruction && (
          <CareRow
            icon={<Droplets size={15} className="text-sky-500" />}
            label="Watering"
            value={reminderState.nextWateringInstruction}
            highlight={reminderState.wateringDue || reminderState.wateringOverdue}
          />
        )}
        {reminderState?.nextFertilizerInstruction && (
          <CareRow
            icon={<Leaf size={15} className="text-amber-600" />}
            label="Fertilizer"
            value={reminderState.nextFertilizerInstruction}
            highlight={reminderState.fertilizerDue}
          />
        )}
        {reminderState?.nextPruningInstruction && (
          <CareRow
            icon={<Scissors size={15} className="text-green-600" />}
            label="Pruning"
            value={reminderState.nextPruningInstruction}
            highlight={reminderState.pruningDue}
            detail={analysis.pruningGeneralGuidance ?? undefined}
          />
        )}

        {/* Static care facts */}
        {analysis.lightNeeds && (
          <CareRow
            icon={<Sun size={15} className="text-amber-400" />}
            label="Light"
            value={analysis.lightNeeds}
            detail={analysis.lightGeneralGuidance ?? undefined}
          />
        )}
        {analysis.placementGuidance && (
          <CareRow
            icon={<MapPin size={15} className="text-stone-400" />}
            label="Placement"
            value={analysis.placementGuidance}
            detail={analysis.placementGeneralGuidance ?? undefined}
          />
        )}

        {/* Health diagnosis if present */}
        {analysis.healthDiagnosis && (
          <div className="rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-700 border border-amber-100">
            <span className="font-medium">Health note: </span>
            {analysis.healthDiagnosis}
          </div>
        )}

        {/* Goal suggestions */}
        {analysis.goalSuggestions && (
          <div className="rounded-md bg-green-50 px-3 py-2 text-xs text-green-700 border border-green-100">
            <span className="font-medium">Goal guidance: </span>
            {analysis.goalSuggestions}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function CareRow({
  icon,
  label,
  value,
  highlight,
  detail,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  highlight?: boolean;
  detail?: string;
}) {
  return (
    <div className={`flex gap-2 text-sm ${highlight ? "text-stone-800" : "text-stone-600"}`}>
      <span className="mt-0.5 flex-shrink-0">{icon}</span>
      <div>
        <span className="font-medium text-xs text-stone-400 uppercase tracking-wide block mb-0.5">
          {label}
        </span>
        <span>{value}</span>
        {detail && (
          <p className="text-xs text-stone-400 mt-1 leading-snug">{detail}</p>
        )}
      </div>
    </div>
  );
}
