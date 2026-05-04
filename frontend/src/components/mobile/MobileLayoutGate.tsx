"use client";

import type { ReactNode } from "react";
import { MobileShell } from "@/components/mobile/MobileShell";
import { useIsMobile } from "@/lib/MobileLayoutContext";

/**
 * Conditionally renders the mobile shell or the desktop children based on the
 * isMobile context value (matchMedia-driven, SSR-safe default false).
 *
 * SSR always produces the desktop render (isMobile defaults to false), so
 * desktop users never see the mobile shell. On mobile, the shell swaps in
 * client-side after the first matchMedia check.
 */
export function MobileLayoutGate({ children }: { children: ReactNode }) {
  const isMobile = useIsMobile();

  if (isMobile) {
    return <MobileShell />;
  }

  return <>{children}</>;
}
