This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

### Access from other devices on your home network (LAN)

Stay on **HTTP** on the LAN (`http://<IP>:3000`). Do **not** open WAN port forwarding for 3000/8080 if you want to avoid exposing the dev stack to the internet.

1. Start Spring on the dev machine (`backend`, `local` profile). Postgres is expected on **`localhost:5434`** (see repo root `docker-compose.yml`).
2. Run **`npm run dev:lan`** here (same as `next dev --hostname 0.0.0.0`).
3. **API + local images:** The UI uses **same-origin** URLs under **`/api`** and **`/images`**. `next.config.ts` rewrites those (in development only) to **`http://127.0.0.1:8080`**. Override the upstream with **`PLANTED_API_PROXY_ORIGIN`** if Spring is not on loopback:8080.
4. **`NEXT_PUBLIC_API_URL`:** Leave **unset** in normal local/LAN dev so `src/lib/api.ts` uses relative `/api` and the proxy. Set it when the built UI must call a specific API origin (e.g. production).
5. **Next.js 16** restricts cross-origin access to some dev internals. This repo fills **`allowedDevOrigins`** from private IPv4 addresses at dev-server startup. Add more with **`PLANTED_DEV_EXTRA_ORIGINS`** (comma-separated `host` or `host:port` values, per Next docs).
6. On another device: open **`http://<DEV_MACHINE_LAN_IP>:3000`**.

**Backend:** Local profile serves files at `/images/**` and returns **root-relative** `/images/...` in JSON unless **`planted.storage.public-base-url`** is set (only needed for clients that load images directly from Spring).

Repo-level overview: **[../README.md](../README.md)** (architecture + ship checklist). Helper script: **`../scripts/planted-lan-dev.sh`**.

The plant detail **Care** panel shows a short primary line plus gray educational text for watering, fertilizer, pruning, light, and placement when the backend provides the matching `latestAnalysis` fields (pruning/light/placement general lines appear after registration or reanalysis with the current schema). For **outdoor** plants with coordinates, the backend may also persist a **`weatherCareNote`** on `reminderState`; the UI shows it under the care rows when present. **Growing context** and coordinates are set at upload (`growingContext`, optional `latitude`/`longitude` parts) or later via **`PATCH /api/plants/{id}/growing`** (see `updatePlantGrowing` in `src/lib/api.ts`).

The **Your Plants** list (`/plants`) includes an optional **Your Location** field; it calls `getUserLocation` / `putUserLocation` in `src/lib/api.ts` and saves on blur so care and history LLM prompts can use typical regional climate context (not live weather).

The **About** panel **History** section prefers **`historyDailyDigests`** (one narrative tile per local calendar day) when the backend returns them after **Generate / Refresh summary**; otherwise it uses structured `historyEntries` plus legacy summary text parsing.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
