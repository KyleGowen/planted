"use client";

import { createContext, useContext } from "react";
import type { ReactNode } from "react";
import { useMobileLayout } from "@/hooks/useMobileLayout";

interface MobileLayoutContextValue {
  isMobile: boolean;
}

const MobileLayoutContext = createContext<MobileLayoutContextValue>({
  isMobile: false,
});

export function MobileLayoutProvider({ children }: { children: ReactNode }) {
  const { isMobile } = useMobileLayout();

  return (
    <MobileLayoutContext.Provider value={{ isMobile }}>
      {children}
    </MobileLayoutContext.Provider>
  );
}

/**
 * Returns the current mobile layout state. Works anywhere inside MobileLayoutProvider.
 * Defaults to false if used outside the provider (e.g. in tests).
 */
export function useIsMobile(): boolean {
  return useContext(MobileLayoutContext).isMobile;
}
