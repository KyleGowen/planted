<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

## Local and LAN development (Planted)

- **Next.js 16** in this repo. Dev server may use **Turbopack** (`next dev`).
- **API client** (`src/lib/api.ts`): In development, prefer **relative** `/api/...` (empty base URL) so the browser stays on the Next origin. **`next.config.ts`** rewrites `/api/:path*` and `/images/:path*` to Spring at **`PLANTED_API_PROXY_ORIGIN`** (default `http://127.0.0.1:8080`). Do not rely on `localhost:8080` in the browser on other devices.
- **`allowedDevOrigins`:** Populated from the machine’s private IPv4 addresses so opening the app via `http://192.168.x.x:3000` works. Extend with **`PLANTED_DEV_EXTRA_ORIGINS`** if needed.
- **Images:** DTOs may use root-relative `/images/...`; **`PlantImageStrip`** must treat **`/`** URLs like app-hosted images (not only `http:`).
- **Shipping:** If the user says **ship**, update **README** / this file when conventions change, and follow **`scripts/ship-checklist.md`** + **`.cursor/rules/ship.mdc`**.
- **Plant detail types:** `PlantDetailResponse` may include **`historyDailyDigests`** (preferred History tiles in About when non-empty); align `src/types/plant.ts` with the backend contract.
