import { API_BASE_URL, fetchApi, handleAuthExpired } from "@/shared/api/client";
import type {
  BedZonePlacementProfile,
  FarmStatusMapData,
  House,
  OrchidGroup,
  OrchidGroupWorkHistory,
  VarietyOption,
  WorkRecord,
  WorkRecordTargetType,
  WorkType,
} from "@/entities/farm/types";
import type {
  DerivedOrchidGroup,
  MutationPayload,
  MultiCreateOrchidGroupRow,
  MultiCreateWorkResult,
  OrchidGroupCollection,
  PreciseMovePayload,
  WorkRecordQuickPayload,
} from "../model/types";

export async function createMultipleOrchidGroups(payload: {
  idempotencyKey: string;
  title: string;
  workDate: string;
  worker: string | null;
  memo: string | null;
  rows: MultiCreateOrchidGroupRow[];
}): Promise<MultiCreateWorkResult> {
  const response = await fetch(`${API_BASE_URL}/work-operations/multi-create`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(
      resolveErrorMessage(body, "난 묶음을 일괄 생성하지 못했습니다."),
    );
  }
  return (body as { data: MultiCreateWorkResult }).data;
}

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
    { method: "DELETE", credentials: "include" },
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
      credentials: "include",
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
      credentials: "include",
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

export function getOrchidGroupWorkHistory(orchidGroupId: number) {
  return fetchApi<OrchidGroupWorkHistory[]>(
    `/orchid-groups/${orchidGroupId}/work-history`,
  );
}

export function getOrchidGroupCollections() {
  return fetchApi<OrchidGroupCollection[]>("/orchid-group-collections");
}

export function getDerivedOrchidGroups(houseId: number) {
  const params = new URLSearchParams({ houseId: String(houseId) });
  return fetchApi<DerivedOrchidGroup[]>(
    `/orchid-groups/derived-groups?${params.toString()}`,
  );
}

export function getDerivedOrchidGroupMembers(
  groupKey: string,
  houseId: number,
) {
  const params = new URLSearchParams({ houseId: String(houseId) });
  return fetchApi<OrchidGroup[]>(
    `/orchid-groups/derived-groups/${encodeURIComponent(groupKey)}/members?${params.toString()}`,
  );
}

export function createOrchidGroupCollection(name: string) {
  return submitCollectionMutation("/orchid-group-collections", "POST", {
    name,
  });
}

export function addOrchidGroupCollectionMember(
  collectionId: number,
  orchidGroupIds: number | number[],
) {
  return submitCollectionMutation(
    `/orchid-group-collections/${collectionId}/members`,
    "POST",
    {
      orchidGroupIds: Array.isArray(orchidGroupIds)
        ? orchidGroupIds
        : [orchidGroupIds],
    },
  );
}

export function removeOrchidGroupCollectionMember(
  collectionId: number,
  orchidGroupId: number,
) {
  return submitCollectionMutation(
    `/orchid-group-collections/${collectionId}/members/${orchidGroupId}`,
    "DELETE",
  );
}

export function archiveOrchidGroupCollection(collectionId: number) {
  return submitCollectionMutation(
    `/orchid-group-collections/${collectionId}/archive`,
    "POST",
  );
}

export async function createOrchidWorkRecord(
  payload: WorkRecordQuickPayload,
): Promise<WorkRecord> {
  const response = await fetch(`${API_BASE_URL}/work-records`, {
    method: "POST",
    credentials: "include",
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
    credentials: "include",
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
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(resolveErrorMessage(body, "저장하지 못했습니다."));
  }
}

async function submitCollectionMutation(
  path: string,
  method: "POST" | "DELETE",
  payload?: object,
): Promise<OrchidGroupCollection> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    credentials: "include",
    headers: payload ? { "Content-Type": "application/json" } : undefined,
    body: payload ? JSON.stringify(payload) : undefined,
  });
  await handleAuthExpired(response);
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(
      resolveErrorMessage(body, "사용자 그룹을 변경하지 못했습니다."),
    );
  }
  return (body as { data: OrchidGroupCollection }).data;
}
