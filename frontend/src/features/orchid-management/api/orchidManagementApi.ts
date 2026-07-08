import { API_BASE_URL, fetchApi } from "@/shared/api/client";
import type {
  BedZonePlacementProfile,
  FarmStatusMapData,
  House,
  OrchidGroup,
  VarietyOption,
  WorkRecord,
  WorkRecordTargetType,
  WorkType,
} from "@/entities/farm/types";
import type {
  MutationPayload,
  PreciseMovePayload,
  WorkRecordQuickPayload,
} from "../model/types";

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
  payload: PreciseMovePayload,
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/orchid-groups/${orchidGroupId}/move`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...payload, memo: payload.memo.trim() || null }),
    },
  );
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(resolveErrorMessage(body, "이동하지 못했습니다."));
  }
}

export function getBedZonePlacementProfile(bedZoneId: number) {
  return fetchApi<BedZonePlacementProfile>(
    `/bed-zones/${bedZoneId}/placement-profile`,
  );
}

export async function saveBedZonePlacementProfile(
  profile: BedZonePlacementProfile,
): Promise<BedZonePlacementProfile> {
  const response = await fetch(
    `${API_BASE_URL}/bed-zones/${profile.bedZoneId}/placement-profile`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ capacities: profile.capacities }),
    },
  );
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(
      resolveErrorMessage(body, "다이 정밀 설정을 저장하지 못했습니다."),
    );
  }
  return (body as { data: BedZonePlacementProfile }).data;
}

export function getOrchidManagementMap() {
  return fetchApi<FarmStatusMapData>("/farm-status/map");
}

export function searchOrchidGroups({
  keyword,
  status,
}: {
  keyword: string;
  status: string;
}) {
  const params = new URLSearchParams();
  if (keyword.trim()) {
    params.set("keyword", keyword.trim());
  }
  if (status.trim()) {
    params.set("status", status.trim());
  }
  const query = params.toString();
  return fetchApi<OrchidGroup[]>(`/orchid-groups${query ? `?${query}` : ""}`);
}

type VarietySearchResponse = {
  content: Array<{
    id: number;
    genus: string;
    name: string;
    defaultPotSize: string | null;
    active: boolean;
  }>;
};

export async function searchOrchidVarieties(
  keyword: string,
): Promise<VarietyOption[]> {
  const params = new URLSearchParams({
    active: "true",
    size: "20",
  });
  if (keyword.trim()) {
    params.set("keyword", keyword.trim());
  }

  const result = await fetchApi<VarietySearchResponse>(
    `/varieties?${params.toString()}`,
  );

  return result.content.map((item) => ({
    id: item.id,
    genus: item.genus,
    name: item.name,
    defaultPotSize: item.defaultPotSize,
    active: item.active,
  }));
}

export function getHouse(houseId: number) {
  return fetchApi<House>(`/houses/${houseId}`);
}

export function getOrchidWorkTypes() {
  return fetchApi<WorkType[]>("/work-types");
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
      resolveErrorMessage(payload, "대상 동을 불러오지 못했습니다."),
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
