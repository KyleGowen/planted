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

The plant detail **Care** panel organizes watering, fertilizer, pruning, light, and placement as **accordion topics** in `CareTopicAccordion` (only one expanded at a time; all start collapsed). Expanded rows show the primary instruction plus gray educational text when the backend provides the matching `latestAnalysis` / reminder fields (pruning/light/placement general lines appear after registration or reanalysis with the current schema). When a reminder is **due** for water, fertilizer, or prune, the collapsed row shows an **amber outline warning icon** instead of repeating the instruction in the header; expand the row to read the full line (screen readers still receive the full instruction via visually hidden text). **Placement** shows only model-generated lines; the pencil (hover to reveal, like the plant name) opens a dialog to edit **placement notes** (persisted as `location` on the plant). Saving calls **`PATCH /api/plants/{id}/placement`** (`updatePlantPlacement` in `src/lib/api.ts`), which returns **202** with the same payload shape as reanalysis and kicks off a full refresh job. For **outdoor** plants with coordinates, the backend may also persist a **`weatherCareNote`** on `reminderState`; the UI shows it in the header growing line when present and in the Care panel. **Growing context** and coordinates are set at upload (`growingContext`, optional `latitude`/`longitude` parts) or later via **`PATCH /api/plants/{id}/growing`** (see `updatePlantGrowing` in `src/lib/api.ts`). On **`/plants/[id]`**, the header shows geo when set, then **Indoor** or **Outdoor** with an optional short snippet from `location` when indoor, coords hints, and a hover-reveal pencil that opens the growing dialog. Below **Archive**, a compact **observation** control (`addHistoryNote` / `addHistoryImage` in `src/lib/api.ts`) captures text and optional photos; the combined timeline appears in the About **History** section, not in the left column.

**TanStack Query Devtools** (floating icon) are **off** by default. In development, set **`NEXT_PUBLIC_QUERY_DEVTOOLS=true`** in `.env.local` to enable them (`src/lib/providers.tsx`).

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
