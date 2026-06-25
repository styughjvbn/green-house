import { API_BASE_URL, fetchApi } from "@/shared/api/client";
import type {
  FarmStatusMapData,
  House,
  WorkRecord,
  WorkRecordTargetType,
} from "@/entities/farm/types";
import type { MutationPayload, WorkRecordQuickPayload } from "../model/types";

type ApiErrorPayload = {
  error?: {
    message?: string;
  };
};

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function resolveErrorMessage(payload: unknown, fallback: string): string {
  const maybeError = payload as ApiErrorPayload | null;
  return maybeError?.error?.message ?? fallback;
}

export async function createOrchidGroup(
  payload: MutationPayload & { bedZoneId: number },
): Promise<void> {
  await submitOrchidMutation("/orchid-groups", "POST", payload);
}

export async function updateOrchidGroup(
  orchidGroupId: number,
  payload: MutationPayload,
): Promise<void> {
  await submitOrchidMutation(
    `/orchid-groups/${orchidGroupId}`,
    "PATCH",
    payload,
  );
}

export async function deleteOrchidGroup(orchidGroupId: number): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/orchid-groups/${orchidGroupId}`,
    { method: "DELETE" },
  );
  if (!response.ok) {
    const body = await readJson(response);
    throw new Error(resolveErrorMessage(body, "삭제하지 못했습니다."));
  }
}

export async function moveOrchidGroup(
  orchidGroupId: number,
  toBedZoneId: number,
  memo: string,
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/orchid-groups/${orchidGroupId}/move`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        toBedZoneId,
        memo: memo.trim() || null,
      }),
    },
  );
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(resolveErrorMessage(body, "이동하지 못했습니다."));
  }
}

export function getOrchidManagementMap() {
  return fetchApi<FarmStatusMapData>("/farm-status/map");
}

export function getHouse(houseId: number) {
  return fetchApi<House>(`/houses/${houseId}`);
}

export function getOrchidWorkTypes() {
  return fetchApi<string[]>("/work-types");
}

export function getOrchidWorkRecords(
  targetType: WorkRecordTargetType,
  targetId: number | null,
) {
  const params = new URLSearchParams({ targetType });
  if (targetId !== null) {
    params.set("targetId", String(targetId));
  }
  return fetchApi<WorkRecord[]>(`/work-records?${params.toString()}`);
}

export async function createOrchidWorkRecord(
  payload: WorkRecordQuickPayload,
): Promise<WorkRecord> {
  const response = await fetch(`${API_BASE_URL}/work-records`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(
      resolveErrorMessage(body, "작업 이력을 저장하지 못했습니다."),
    );
  }
  return (body as { data: WorkRecord }).data;
}

export async function fetchHouse(houseId: number): Promise<House> {
  const response = await fetch(`${API_BASE_URL}/houses/${houseId}`, {
    cache: "no-store",
  });
  const payload = await readJson(response);
  if (!response.ok) {
    throw new Error(
      resolveErrorMessage(payload, "목적 동을 불러오지 못했습니다."),
    );
  }
  return (payload as { data: House }).data;
}

async function submitOrchidMutation(
  path: string,
  method: "POST" | "PATCH",
  payload: MutationPayload & { bedZoneId?: number },
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(resolveErrorMessage(body, "저장하지 못했습니다."));
  }
}
