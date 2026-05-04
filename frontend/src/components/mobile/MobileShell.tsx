"use client";

import { useState } from "react";
import { BottomTabBar } from "./BottomTabBar";
import { HomeTab } from "./HomeTab";
import { MyPlantsTab } from "./MyPlantsTab";
import { ActivityTab } from "./ActivityTab";
import { CameraModal } from "./CameraModal";
import { ProfileTab } from "./ProfileTab";

export type MobileTab = "home" | "plants" | "activity" | "profile";

/**
 * Top-level mobile layout shell. Owns tab state and renders the appropriate
 * tab content between a full-bleed area and the bottom tab bar.
 * The camera tab opens a full-screen modal overlay instead of swapping content.
 */
export function MobileShell() {
  const [activeTab, setActiveTab] = useState<MobileTab>("home");
  const [cameraOpen, setCameraOpen] = useState(false);

  function handleTabSelect(tab: MobileTab | "camera") {
    if (tab === "camera") {
      setCameraOpen(true);
    } else {
      setActiveTab(tab);
    }
  }

  return (
    <div className="flex flex-col h-[100dvh] overflow-hidden bg-stone-50">
      {/* Tab content area */}
      <div className="flex-1 overflow-hidden relative">
        {activeTab === "home" && <HomeTab />}
        {activeTab === "plants" && <MyPlantsTab />}
        {activeTab === "activity" && <ActivityTab />}
        {activeTab === "profile" && <ProfileTab />}
      </div>

      <BottomTabBar activeTab={activeTab} onTabSelect={handleTabSelect} />

      {cameraOpen && (
        <CameraModal
          onClose={() => setCameraOpen(false)}
          onComplete={() => {
            setCameraOpen(false);
            setActiveTab("home");
          }}
        />
      )}
    </div>
  );
}
