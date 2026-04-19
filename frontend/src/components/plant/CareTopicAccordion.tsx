"use client";

import { useId } from "react";
import { AlertTriangle } from "lucide-react";
import { cn } from "@/lib/utils";

export interface CareTopicAccordionProps {
  icon: React.ReactNode;
  label: string;
  value: string;
  /** When true, primary line uses stronger emphasis (water / fert / prune urgency). */
  highlight?: boolean;
  /** Placement-style softer primary when analysis guidance is generic. */
  valueMuted?: boolean;
  detail?: string;
  /** When true, collapsed header shows a warning icon (reminder due / action required). */
  actionNeeded: boolean;
  isOpen: boolean;
  onToggle: () => void;
  headerAccessory?: React.ReactNode;
  /** When true, render a subtle "Updating…" hint next to the label — the section's
   *  bio prompt is being refreshed in the background. */
  refreshing?: boolean;
}

export function CareTopicAccordion({
  icon,
  label,
  value,
  highlight,
  valueMuted,
  detail,
  actionNeeded,
  isOpen,
  onToggle,
  headerAccessory,
  refreshing,
}: CareTopicAccordionProps) {
  const uid = useId();
  const panelId = `${uid}-panel`;
  const headerId = `${uid}-header`;

  const valueClass = cn(
    "block text-sm leading-snug",
    valueMuted ? "text-stone-500" : highlight ? "text-stone-800 font-medium" : "text-stone-600"
  );

  return (
    <div className="group flex gap-2 text-sm">
      <span className="mt-0.5 flex-shrink-0">{icon}</span>
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <button
            type="button"
            id={headerId}
            aria-expanded={isOpen}
            aria-controls={panelId}
            onClick={onToggle}
            className={cn(
              "min-w-0 flex-1 rounded-md -mx-1 px-1 py-0.5 text-left transition-colors",
              "hover:bg-stone-50/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-stone-200 focus-visible:ring-offset-1"
            )}
          >
            {!isOpen ? (
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium uppercase tracking-wide text-stone-400">
                  {label}
                  {refreshing ? (
                    <span className="ml-1.5 normal-case tracking-normal text-[10px] text-stone-400 italic">
                      Updating…
                    </span>
                  ) : null}
                </span>
                {actionNeeded ? (
                  <>
                    <span className="sr-only">
                      {label}: action needed. {value}
                    </span>
                    <span className="flex shrink-0 items-center" aria-hidden>
                      <AlertTriangle
                        size={14}
                        strokeWidth={2}
                        className={cn(
                          "text-amber-500",
                          highlight && "text-amber-600"
                        )}
                      />
                    </span>
                  </>
                ) : null}
              </div>
            ) : (
              <span className="text-xs font-medium uppercase tracking-wide text-stone-400">
                {label}
                {refreshing ? (
                  <span className="ml-1.5 normal-case tracking-normal text-[10px] text-stone-400 italic">
                    Updating…
                  </span>
                ) : null}
              </span>
            )}
          </button>
          {headerAccessory ? <div className="shrink-0 pt-0.5">{headerAccessory}</div> : null}
        </div>

        <div
          id={panelId}
          role="region"
          aria-labelledby={headerId}
          className={cn(
            "grid transition-[grid-template-rows] duration-300 ease-out",
            isOpen ? "grid-rows-[1fr]" : "grid-rows-[0fr]"
          )}
        >
          <div className="min-h-0 overflow-hidden">
            <div
              className={cn(
                "pt-0.5 transition-opacity duration-200 ease-out",
                isOpen ? "opacity-100" : "opacity-0"
              )}
              aria-hidden={!isOpen}
            >
              <span className={valueClass}>{value}</span>
              {detail ? (
                <p className="mt-1 text-xs leading-snug text-stone-400">{detail}</p>
              ) : null}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
