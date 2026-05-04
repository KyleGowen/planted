"use client";

import { useSyncExternalStore } from "react";

const MOBILE_MEDIA_QUERY = "(max-aspect-ratio: 1/1) and (max-width: 768px)";

function subscribe(onStoreChange: () => void) {
  const mq = window.matchMedia(MOBILE_MEDIA_QUERY);
  mq.addEventListener("change", onStoreChange);
  return () => mq.removeEventListener("change", onStoreChange);
}

function getSnapshot() {
  return window.matchMedia(MOBILE_MEDIA_QUERY).matches;
}

function getServerSnapshot() {
  return false;
}

/**
 * Detects portrait/mobile viewport via aspect-ratio + width media query.
 * SSR-safe: defaults to false on the server, then hydrates correctly on the client.
 */
export function useMobileLayout(): { isMobile: boolean } {
  const isMobile = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
  return { isMobile };
}
