# Planted

An async-first plant tracking application. Upload photos of your plants, get AI-powered species identification and care guidance, and track watering, fertilizing, and pruning history over time.

---

## Architecture Overview

```
┌─────────────────────┐   dev: same host   ┌─────────────────────────┐
│   Next.js Frontend  │   /api + /images   │  Spring Boot Backend    │
│   (App Router)      │   proxied to ────► │  :8080 (local profile)  │
│   :3000             │   127.0.0.1:8080   │                         │
│   TanStack Query    │                    │  PlantController        │
│   shadcn/ui         │                    │  ReminderController     │
└─────────────────────┘                    └──────────┬──────────────┘
                                                  │
                              ┌───────────────────┼────────────────────┐
                              │                   │                    │
                        ┌─────▼──────┐   ┌───────▼───────┐   ┌───────▼──────┐
                        │ PostgreSQL  │   │  Job Queue    │   │  OpenAI      │
                        │ (Flyway)    │   │  (SQS/local)  │   │  Responses   │
                        └────────────┘   └───────┬───────┘   │  API         │
                                                 │            └──────────────┘
                                        ┌────────▼──────────┐
                                        │  PlantJobWorker   │
                                        │                   │
                                        │ - AnalysisProc.   │
                                        │ - IllustrProc.    │
                                        │ - PruningProc.    │
                                        │ - ReminderProc.   │
                                        └───────────────────┘
```

### Async-first design

Every slow operation — LLM plant analysis, illustrated image generation, reminder recomputation — runs in a background job. The HTTP request thread returns immediately with a `202 Accepted` and a job status. The frontend polls (every 3 seconds) while jobs are in progress and shows clear PENDING → PROCESSING → COMPLETED state.

```
POST /api/plants  →  202 Accepted immediately
                         │
                         ├── [Worker] PLANT_REGISTRATION_ANALYSIS
                         │         └─ OpenAI Responses API (image + structured output)
                         │         └─ Writes genus/species/care fields + JSONB
                         │         └─ Enqueues PLANT_REMINDER_RECOMPUTE
                         │
                         └── [Worker] PLANT_ILLUSTRATION_GENERATION
                                   └─ DALL-E 3 (botanical illustration)
                                   └─ Stores PNG, links to plant record
```

---

## Local Development Setup

### Prerequisites

- Docker Desktop
- Java 21
- Node.js 18+
- Maven

### 1. Start PostgreSQL

```bash
docker-compose up -d
```

This maps Postgres to host port **5434** (container listens on 5432) with:
- database: `planted_db`
- user: `planted`
- password: `planted`

### 2. Start the backend

```bash
cd backend
OPENAI_API_KEY=your_key_here mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The backend starts on `http://localhost:8080` (all interfaces — reachable on your LAN IP for API-only clients).  
Flyway runs migrations from `backend/src/main/resources/db/migration/` on startup.  
Images are stored locally in `./backend/data/images/`.

### 3. Start the frontend

```bash
cd frontend
cp .env.local.example .env.local
npm run dev
```

The frontend starts on `http://localhost:3000`.

---

## LAN development (phones / other machines on Wi‑Fi)

Use this when you open the UI from another device on the same private network (no router WAN port forwarding — that would expose the app to the internet).

1. Start Postgres, Spring (`local` profile), and the frontend with **`npm run dev:lan`** in `frontend/` (listens on `0.0.0.0:3000`).
2. In **development**, the browser talks only to **Next on port 3000**. Next.js **rewrites** `/api/*` and `/images/*` to Spring at `http://127.0.0.1:8080`, so API calls and local plant images work from a phone without `localhost` on the wrong device.
3. Spring returns **root-relative** image paths (`/images/...`) when `planted.storage.public-base-url` is unset; set that property only if something calls Spring **directly** for images (no Next proxy).
4. **CORS** for direct browser→Spring calls is configured via `planted.cors.allowed-origin-patterns` in `application.yml` (localhost, Vercel, private LAN patterns). Same-origin traffic through Next does not need CORS for `/api`.
5. Next.js 16 **allowedDevOrigins** is populated from this machine’s private IPv4 addresses at dev startup; use **`PLANTED_DEV_EXTRA_ORIGINS`** (comma-separated) if you need more hosts.

Details and env hints: **[frontend/README.md](frontend/README.md)**. Quick helper (prints LAN IP and start lines): **`./scripts/planted-lan-dev.sh`**.

---

## Ship (commit + push)

**Ship** means documentation and Cursor context are updated when they apply to the change — not only git. Before pushing:

- Follow **[scripts/ship-checklist.md](scripts/ship-checklist.md)**.
- Observe **[.cursor/rules/ship.mdc](.cursor/rules/ship.mdc)**.

If you intend **code only** with no doc/context pass, say so explicitly (e.g. “ship code only”).

---

## UI Layout

### Plant Detail Page (Bio Page)
The plant detail page is a fixed-height, no-scroll landscape layout designed for web app use. It fills the full viewport (`100dvh`) with two columns:

- **Left column (34%):** Hero photo (fills available height) → Reference + Photo history thumbnails → History observation input pinned to the bottom
- **Right column (flex-1):** Name/species/location header → PlantStatusCard → About This Plant + Care panels side-by-side (both scroll internally, bottoms align with thumbnail row)

The back link (`← All plants`) is absolutely positioned so it doesn't consume layout space.

### Plant List Page
Each list card thumbnail uses: `illustratedImage` → `originalImage` (user's own upload) → Sprout placeholder. Internet-sourced reference images (`HEALTHY_REFERENCE`) are never used as list thumbnails — the list shows only the user's personal photos.

---

## Environment Variables

### Backend (application-local.yml defaults, override via env)

| Variable | Description | Local default |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI API key (required for analysis) | — |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5434/planted_db` (matches `docker-compose` host port) |
| `SPRING_DATASOURCE_USERNAME` | DB username | `planted` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `planted` |

### Backend (prod profile, via env)

| Variable | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | DB username |
| `DB_PASSWORD` | DB password |
| `OPENAI_API_KEY` | OpenAI API key |
| `S3_BUCKET` | S3 bucket name |
| `AWS_REGION` | AWS region (default: `us-east-1`) |
| `SQS_QUEUE_URL` | SQS queue URL for background jobs |

### Frontend

| Variable | Description | Default |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | Backend base URL for browser `fetch` | In **development**, leave unset so requests use same-origin `/api` (Next rewrites to Spring). Set for production builds or when the UI must call a fixed API host. |
| `PLANTED_API_PROXY_ORIGIN` | (Next dev only) Where to proxy `/api` and `/images` | `http://127.0.0.1:8080` |
| `PLANTED_DEV_EXTRA_ORIGINS` | (Next dev only) Extra entries for `allowedDevOrigins` | — |

---

## Reminder Strategy

Reminder state is **precomputed and persisted** in the `plant_reminder_state` table. The plant list page reads directly from this table — no live LLM calls.

Reminder state is recomputed (via `PLANT_REMINDER_RECOMPUTE` job) whenever:
- A watering event is recorded
- A fertilizer event is recorded
- A prune event is recorded
- Registration or reanalysis completes
- A scheduled periodic job runs (future: daily cron)

The recompute logic in `PlantReminderService`:
1. Reads the latest completed `PlantAnalysis` for watering/fertilizer/pruning frequency
2. Reads the most recent care event from each history table
3. Computes days elapsed vs frequency → sets `wateringDue`, `fertilizerDue`, `pruningDue`
4. Generates plain-language next-step instructions (pruning line prefers `pruning_action_summary`, then legacy `pruning_guidance`, then `pruning_general_guidance`)
5. Upserts `PlantReminderState`

Registration and reanalysis persist **paired care text** for the UI: short primary lines plus separate general guidance for light (`light_needs` / `light_general_guidance`), placement (`placement_guidance` / `placement_general_guidance`), and pruning (`pruning_action_summary` / `pruning_general_guidance`). The API exposes these on `latestAnalysis` (`AnalysisSummaryDto`). Existing analyses only gain the new gray lines after a new registration or reanalysis.

---

## Local Storage vs S3

Controlled by Spring profiles:

| Profile | Image storage | Queue |
|---|---|---|
| `local` | `LocalImageStorageService` → `./data/images/` | `LocalPlantJobPublisher` (Spring events, async) |
| `prod` | `S3ImageStorageService` → AWS S3 | `SqsPlantJobPublisher` → Amazon SQS |

Both implement `ImageStorageService` and `PlantJobPublisher` interfaces — no application logic changes between profiles.

Local files are served by Spring at `/images/**` (resource handler). With the **Next dev proxy**, clients typically load `http://<next-host>:3000/images/...`, which forwards to Spring. For API consumers that bypass Next, optionally set `planted.storage.public-base-url` so JSON includes absolute URLs.

---

## SQS Configuration

In production, `SqsPlantJobPublisher` sends JSON `PlantJobMessage` objects to an SQS standard queue. Message format:

```json
{
  "jobType": "PLANT_REGISTRATION_ANALYSIS",
  "plantId": 123,
  "analysisId": 456,
  "imageIds": [789]
}
```

To set up SQS:
1. Create a standard queue in your AWS account (474120878015)
2. Set `SQS_QUEUE_URL` to the queue URL
3. Attach an IAM role/policy with `sqs:SendMessage` and `sqs:ReceiveMessage`

For local dev, the `LocalPlantJobPublisher` publishes Spring `ApplicationEvent`s, which `PlantJobWorker` handles asynchronously on a dedicated thread pool (`plantJobExecutor`). No SQS setup required.

---

## Prompt Storage

Prompts are versioned in the `llm_prompts` table (seeded by `V6__create_llm_prompts.sql`). Every OpenAI call is audited in `llm_requests` with the rendered prompt and response — enabling full debuggability and future prompt iteration without losing history.

Prompt keys:
- `plant_registration_analysis_v1` — species ID + care guidance (versioned in DB; structured output includes layered light/placement/pruning fields, with `pruningActionSummary` requiring when and how much for the individual plant). When the user has saved a home/growing-site address (`user_physical_addresses`), the rendered registration user prompt includes it as climate context (typical regional seasons only—not live weather).
- `plant_info_panel_v1` — species facts, history, uses
- `plant_reminder_recompute_v1` — care scheduling
- `pruning_analysis_v1` — conservative pruning guidance from photos

---

## Database Schema

Versioned SQL migrations live in `backend/src/main/resources/db/migration/` (`V__*.sql`). Flyway applies them on startup. See that directory for the current set (plants, images, analyses—including paired care guidance columns on `plant_analyses`, events, reminders, LLM audit, history, species overview, history summary, etc.).

---

## Project Structure

```
planted/
├── backend/
│   └── src/main/java/com/planted/
│       ├── controller/     # PlantController, ReminderController
│       ├── service/        # PlantCommandService, PlantQueryService, PlantReminderService
│       ├── worker/         # PlantJobWorker + processors
│       ├── client/         # OpenAiPlantClient, OpenAiImageGenerationClient
│       ├── repository/     # Spring Data JPA repositories
│       ├── entity/         # JPA entities
│       ├── dto/            # Request/response records
│       ├── storage/        # ImageStorageService + impls
│       ├── queue/          # PlantJobPublisher + impls
│       ├── mapper/         # PlantMapper
│       └── config/         # AwsConfig, WebConfig, AsyncConfig
├── frontend/
│   └── src/
│       ├── app/            # Next.js App Router pages
│       ├── components/     # React components
│       ├── lib/            # api.ts, providers.tsx
│       └── types/          # TypeScript interfaces (plant.ts)
├── .cursor/rules/          # Cursor agent rules (.mdc); includes ship.mdc + specialists
├── scripts/                # planted-lan-dev.sh, ship-checklist.md
├── docker-compose.yml
└── README.md
```

---

## AWS Account

- Account: 474120878015 (kgowen)
- Default region: us-east-1
