"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { useState } from "react";
import { MobileLayoutProvider } from "@/lib/MobileLayoutContext";

/** TanStack Query Devtools (floating icon): opt-in via NEXT_PUBLIC_QUERY_DEVTOOLS=true in .env.local */
const showReactQueryDevtools =
  process.env.NODE_ENV === "development" &&
  process.env.NEXT_PUBLIC_QUERY_DEVTOOLS === "true";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 2,
          },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <MobileLayoutProvider>
        {children}
      </MobileLayoutProvider>
      {showReactQueryDevtools ? (
        <ReactQueryDevtools initialIsOpen={false} />
      ) : null}
    </QueryClientProvider>
  );
}
