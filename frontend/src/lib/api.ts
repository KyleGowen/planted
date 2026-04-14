import type {
  CreatePlantResponse,
  PlantDetailResponse,
  PlantListItemResponse,
  PlantReminderResponse,
  RecordFertilizerRequest,
  RecordWateringRequest,
  RequestHistorySummaryResponse,
  RequestPruningAnalysisResponse,
  RequestReanalysisResponse,
} from "@/types/plant";
import type { UserLocationResponse } from "@/types/user";

/**
 * In development, omit the host so requests stay same-origin (e.g. http://192.168.x.x:3000/api/...).
 * next.config.ts rewrites /api → Spring on 127.0.0.1:8080.
 * Set NEXT_PUBLIC_API_URL only when you need a fixed remote API (e.g. production or special setups).
 *
 * Resolved on each fetch (not at module load) so the client bundle never pins a wrong base after SSR import order.
 */
function apiBaseUrl(): string {
  const explicit = process.env.NEXT_PUBLIC_API_URL?.trim();
  if (explicit) {
    return explicit.replace(/\/+$/, "");
  }
  if (typeof window !== "undefined") {
    return "";
  }
  return "http://127.0.0.1:8080";
}

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${apiBaseUrl()}${path}`, {
    ...options,
    headers: {
      ...(options?.headers ?? {}),
    },
  });

  if (!res.ok) {
    let errorMessage = `API error ${res.status}`;
    try {
      const body = await res.json();
      errorMessage =
        (typeof body.error === "string" && body.error) ||
        (typeof body.message === "string" && body.message) ||
        errorMessage;
    } catch {
      // ignore parse error
    }
    throw new Error(errorMessage);
  }

  if (res.status === 204) return undefined as T;
  return res.json();
}

// ── Plant registration ──────────────────────────────────────────────────────

export async function registerPlant(formData: FormData): Promise<CreatePlantResponse> {
  return apiFetch<CreatePlantResponse>("/api/plants", {
    method: "POST",
    body: formData,
  });
}

// ── Plant list / detail ─────────────────────────────────────────────────────

export async function listPlants(): Promise<PlantListItemResponse[]> {
  return apiFetch<PlantListItemResponse[]>("/api/plants");
}

// ── User location (climate context for care prompts) ────────────────────────

export async function getUserLocation(): Promise<UserLocationResponse> {
  return apiFetch<UserLocationResponse>("/api/user/location");
}

export async function putUserLocation(body: {
  address: string | null;
}): Promise<UserLocationResponse> {
  return apiFetch<UserLocationResponse>("/api/user/location", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function getPlant(id: number): Promise<PlantDetailResponse> {
  return apiFetch<PlantDetailResponse>(`/api/plants/${id}`);
}

// ── Care events ─────────────────────────────────────────────────────────────

export async function recordWatering(
  plantId: number,
  data: RecordWateringRequest
): Promise<void> {
  return apiFetch<void>(`/api/plants/${plantId}/waterings`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function recordFertilizer(
  plantId: number,
  data: RecordFertilizerRequest
): Promise<void> {
  return apiFetch<void>(`/api/plants/${plantId}/fertilizers`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

export async function recordPrune(
  plantId: number,
  prunedAt: string,
  notes?: string,
  image?: File
): Promise<void> {
  const formData = new FormData();
  formData.append("prunedAt", prunedAt);
  if (notes) formData.append("notes", notes);
  if (image) formData.append("image", image);
  return apiFetch<void>(`/api/plants/${plantId}/prunes`, {
    method: "POST",
    body: formData,
  });
}

// ── Pruning analysis ────────────────────────────────────────────────────────

export async function requestPruningAnalysis(
  plantId: number,
  images: File[]
): Promise<RequestPruningAnalysisResponse> {
  const formData = new FormData();
  images.forEach((img) => formData.append("images", img));
  return apiFetch<RequestPruningAnalysisResponse>(
    `/api/plants/${plantId}/pruning-analysis`,
    { method: "POST", body: formData }
  );
}

// ── Plant name update ───────────────────────────────────────────────────────

export async function updatePlantName(plantId: number, name: string | null): Promise<void> {
  return apiFetch<void>(`/api/plants/${plantId}/name`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
}

// ── Reanalysis ──────────────────────────────────────────────────────────────

export async function requestReanalysis(plantId: number): Promise<RequestReanalysisResponse> {
  return apiFetch<RequestReanalysisResponse>(`/api/plants/${plantId}/reanalysis`, {
    method: "POST",
  });
}

// ── Plant history ───────────────────────────────────────────────────────────

export async function addHistoryNote(plantId: number, noteText: string): Promise<void> {
  return apiFetch<void>(`/api/plants/${plantId}/history`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ noteText }),
  });
}

export async function addHistoryImage(
  plantId: number,
  image: File,
  noteText?: string
): Promise<void> {
  const formData = new FormData();
  formData.append("image", image);
  if (noteText) formData.append("noteText", noteText);
  return apiFetch<void>(`/api/plants/${plantId}/history/image`, {
    method: "POST",
    body: formData,
  });
}

export async function requestHistorySummary(
  plantId: number
): Promise<RequestHistorySummaryResponse> {
  return apiFetch<RequestHistorySummaryResponse>(`/api/plants/${plantId}/history/summary`, {
    method: "POST",
    headers: { Accept: "application/json" },
  });
}

// ── Archive / restore ───────────────────────────────────────────────────────

export async function archivePlant(plantId: number): Promise<void> {
  return apiFetch<void>(`/api/plants/${plantId}`, { method: "DELETE" });
}

export async function restorePlant(plantId: number): Promise<void> {
  return apiFetch<void>(`/api/plants/${plantId}/restore`, { method: "POST" });
}

// ── Reminders ───────────────────────────────────────────────────────────────

export async function getWateringReminders(): Promise<PlantReminderResponse[]> {
  return apiFetch<PlantReminderResponse[]>("/api/reminders/watering");
}

export async function getFertilizerReminders(): Promise<PlantReminderResponse[]> {
  return apiFetch<PlantReminderResponse[]>("/api/reminders/fertilizing");
}

export async function getPruningReminders(): Promise<PlantReminderResponse[]> {
  return apiFetch<PlantReminderResponse[]>("/api/reminders/pruning");
}
