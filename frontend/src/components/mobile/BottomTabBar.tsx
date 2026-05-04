"use client";

import { Sprout, Camera, Leaf, History } from "lucide-react";
import { cn } from "@/lib/utils";
import type { MobileTab } from "./MobileShell";

type TabId = MobileTab | "camera";

interface Tab {
  id: TabId;
  label: string;
  icon: React.ReactNode;
  isCamera?: boolean;
}

const TABS: Tab[] = [
  {
    id: "home",
    label: "Home",
    icon: <Sprout size={22} />,
  },
  {
    id: "camera",
    label: "Camera",
    icon: <Camera size={28} />,
    isCamera: true,
  },
  {
    id: "plants",
    label: "My Plants",
    icon: <Leaf size={22} />,
  },
  {
    id: "activity",
    label: "Activity",
    icon: <History size={22} />,
  },
];

interface BottomTabBarProps {
  activeTab: MobileTab;
  onTabSelect: (tab: TabId) => void;
}

export function BottomTabBar({ activeTab, onTabSelect }: BottomTabBarProps) {
  return (
    <nav
      className="relative flex items-end bg-white border-t border-stone-200 shadow-[0_-1px_3px_rgba(0,0,0,0.06)]"
      style={{ paddingBottom: "env(safe-area-inset-bottom, 0px)" }}
    >
      {TABS.map((tab) => {
        const isActive = !tab.isCamera && activeTab === tab.id;

        if (tab.isCamera) {
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => onTabSelect(tab.id)}
              aria-label="Camera — add or update a plant"
              className="flex-1 flex flex-col items-center pb-2 pt-1 relative min-h-[60px]"
            >
              {/* Elevated FAB-style pill */}
              <span className="absolute -top-5 flex items-center justify-center w-14 h-14 rounded-full bg-[hsl(var(--primary))] shadow-md min-w-[44px] min-h-[44px]">
                <span className="text-white">{tab.icon}</span>
              </span>
              <span className="mt-10 text-[10px] font-medium text-stone-500">{tab.label}</span>
            </button>
          );
        }

        return (
          <button
            key={tab.id}
            type="button"
            onClick={() => onTabSelect(tab.id)}
            aria-label={tab.label}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "flex-1 flex flex-col items-center gap-0.5 py-2 min-h-[60px] min-w-[44px]",
              "transition-colors duration-150",
              isActive
                ? "text-[hsl(var(--primary))]"
                : "text-stone-400 hover:text-stone-600"
            )}
          >
            {tab.icon}
            <span className="text-[10px] font-medium">{tab.label}</span>
          </button>
        );
      })}
    </nav>
  );
}
