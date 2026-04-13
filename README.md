# Planted

An async-first plant tracking application. Upload photos of your plants, get AI-powered species identification and care guidance, and track watering, fertilizing, and pruning history over time.

---

## Architecture Overview

```
┌─────────────────────┐      REST      ┌─────────────────────────┐
│   Next.js Frontend  │ ◄────────────► │  Spring Boot Backend    │
│   (App Router)      │                │  :8080                  │
│   TanStack Query    │                │                         │
│   shadcn/ui         │                │  PlantController        │
└─────────────────────┘                │  ReminderController     │
                                       └──────────┬──────────────┘
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

This starts Postgres 15 on port 5432 with:
- database: `planted_db`
- user: `planted`
- password: `planted`

### 2. Start the backend

```bash
cd backend
OPENAI_API_KEY=your_key_here mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The backend starts on `http://localhost:8080`.  
Flyway runs all 7 migrations automatically on startup.  
Images are stored locally in `./backend/data/images/`.

### 3. Start the frontend

```bash
cd frontend
cp .env.local.example .env.local
npm run dev
```

The frontend starts on `http://localhost:3000`.

---

## Environment Variables

### Backend (application-local.yml defaults, override via env)

| Variable | Description | Local default |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI API key (required for analysis) | — |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/planted_db` |
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
| `NEXT_PUBLIC_API_URL` | Backend base URL | `http://localhost:8080` |

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
4. Generates plain-language next-step instructions
5. Upserts `PlantReminderState`

---

## Local Storage vs S3

Controlled by Spring profiles:

| Profile | Image storage | Queue |
|---|---|---|
| `local` | `LocalImageStorageService` → `./data/images/` | `LocalPlantJobPublisher` (Spring events, async) |
| `prod` | `S3ImageStorageService` → AWS S3 | `SqsPlantJobPublisher` → Amazon SQS |

Both implement `ImageStorageService` and `PlantJobPublisher` interfaces — no application logic changes between profiles.

Local images are served at `http://localhost:8080/images/{path}` via a Spring MVC resource handler.

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
- `plant_registration_analysis_v1` — species ID + care guidance
- `plant_info_panel_v1` — species facts, history, uses
- `plant_reminder_recompute_v1` — care scheduling
- `pruning_analysis_v1` — conservative pruning guidance from photos

---

## Database Schema

7 Flyway migrations in `backend/src/main/resources/db/migration/`:

| Migration | Tables |
|---|---|
| V1 | `plants` |
| V2 | `plant_images` |
| V3 | `plant_analyses` |
| V4 | `plant_watering_events`, `plant_fertilizer_events`, `plant_prune_events` |
| V5 | `plant_reminder_state` |
| V6 | `llm_prompts` (with seeded prompt templates) |
| V7 | `llm_requests` (audit log) |

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
├── .cursor/rules/          # Cursor agent rules (6 .mdc files)
├── docker-compose.yml
└── README.md
```

---

## AWS Account

- Account: 474120878015 (kgowen)
- Default region: us-east-1
