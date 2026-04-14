import type { NextConfig } from "next";
import os from "node:os";

const isDev = process.env.NODE_ENV !== "production";

/** Spring Boot (local profile). Browser always calls same host as the Next dev server; this proxy avoids wrong localhost on other devices. */
const LOCAL_API_ORIGIN = process.env.PLANTED_API_PROXY_ORIGIN ?? "http://127.0.0.1:8080";

const DEV_PORT = String(process.env.PORT ?? "3000");

/** Next.js 16 blocks cross-origin dev requests by default; LAN access uses a different Host than localhost. */
function privateLanDevOrigins(): string[] {
  const origins = new Set<string>();
  const extra = process.env.PLANTED_DEV_EXTRA_ORIGINS?.split(",") ?? [];
  for (const raw of extra) {
    const o = raw.trim();
    if (o) origins.add(o);
  }
  for (const nets of Object.values(os.networkInterfaces())) {
    if (!nets) continue;
    for (const net of nets) {
      const isV4 = net.family === "IPv4" || net.family === 4;
      if (!isV4 || net.internal) continue;
      const a = net.address;
      const isPrivate =
        /^192\.168\./.test(a) ||
        /^10\./.test(a) ||
        /^172\.(1[6-9]|2\d|3[01])\./.test(a);
      if (isPrivate) {
        origins.add(a);
        origins.add(`${a}:${DEV_PORT}`);
      }
    }
  }
  return [...origins];
}

const nextConfig: NextConfig = {
  ...(isDev ? { allowedDevOrigins: privateLanDevOrigins() } : {}),
  async rewrites() {
    if (!isDev) {
      return [];
    }
    return [
      {
        source: "/api/:path*",
        destination: `${LOCAL_API_ORIGIN}/api/:path*`,
      },
      {
        source: "/images/:path*",
        destination: `${LOCAL_API_ORIGIN}/images/:path*`,
      },
    ];
  },
  images: {
    // In development, skip Next.js image optimization so localhost URLs work directly.
    // In production, only allow images served from our S3 bucket.
    unoptimized: isDev,
    remotePatterns: isDev
      ? []
      : [
          {
            protocol: "https",
            hostname: "**.amazonaws.com",
          },
        ],
  },
};

export default nextConfig;
