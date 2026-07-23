import { API_BASE_URL, fetchApi, handleAuthExpired } from "@/shared/api/client";
import type {
  BedZonePlacementProfile,
  FarmStatusMapData,
  House,
  OrchidManagementViewport,
  OrchidGroup,
  VarietyOption,
  WorkOperation,
  WorkType,
} from "@/entities/farm/types";
import type {
  DerivedOrchidGroup,
  MutationPayload,
  MultiCreateOrchidGroupRow,
  MultiCreateCancellationEligibility,
  MultiCreateWorkResult,
  OrchidGroupCollection,
  OrchidGroupLineage,
  PreciseMovePayload,
  RepotResultOrchidGroupRow,
  RepotWorkResult,
  WorkOperationCorrections,
  WorkRecordQuickPayload,
  WorkHistoryPage,
} from "../model/types";

export function getOrchidGroupLineage(orchidGroupId: number) {
  return fetchApi<OrchidGroupLineage>(
    `/orchid-groups/${orchidGroupId}/lineage`,
  );
}

export function getWorkOperationCorrections(workOperationId: number) {
  return fetchApi<WorkOperationCorrections>(
    `/work-operations/${workOperationId}/corrections`,
  );
}

export async function createWorkOperationCorrection(
  workOperationId: number,
  payload: {
    idempotencyKey: string;
    title: string;
    workDate: string;
    worker: string | null;
    memo: string | null;
    reason: string;
    orchidGroupAdjustments: Array<{
      orchidGroupId: number;
      quantity: number;
      status: string;
    }>;
  },
): Promise<WorkOperationCorrections> {
  const response = await fetch(
    `${API_BASE_URL}/work-operations/${workOperationId}/corrections`,
    {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
  );
  await handleAuthExpired(response);
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(
      resolveErrorMessage(body, "보정 작업을 완료하지 못했습니다."),
    );
  }
  return (body as { data: WorkOperationCorrections }).data;
}

export async function executeRepotWork(payload: {
  idempotencyKey: string;
  title: string;
  workDate: string;
  worker: string | null;
  memo: string | null;
  sourceOrchidGroupId: number;
  inputQuantity: number;
  lossQuantity: number;
  lossReason: string | null;
  results: RepotResultOrchidGroupRow[];
  inheritCollectionIds: number[];
}): Promise<RepotWorkResult> {
  const response = await fetch(`${API_BASE_URL}/work-operations/repot`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(
      resolveErrorMessage(body, "분갈이 작업을 완료하지 못했습니다."),
    );
  }
  return (body as { data: RepotWorkResult }).data;
}

export function getMultiCreateCancellationEligibility(workOperationId: number) {
  return fetchApi<MultiCreateCancellationEligibility>(
    `/work-operations/${workOperationId}/cancel-eligibility`,
  );
}

export async function cancelMultiCreateWork(workOperationId: number) {
  const response = await fetch(
    `${API_BASE_URL}/work-operations/${workOperationId}/cancel-created-orchid-groups`,
    { method: "POST", credentials: "include" },
  );
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(
      resolveErrorMessage(body, "다중 생성 작업을 취소하지 못했습니다."),
    );
  }
  return (body as { data: MultiCreateWorkResult }).data;
}

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

export async function getOrchidManagementMap(): Promise<FarmStatusMapData> {
  const houses = await fetchApi<House[]>("/houses");

  return {
    houses: houses.map((house) => {
      const orchidGroups = house.physicalBeds.flatMap((bed) =>
        bed.bedZones.flatMap((zone) => zone.orchidGroups),
      );
      return {
        houseId: house.id,
        houseNumber: house.number,
        houseName: house.name,
        orchidGroupCount: orchidGroups.length,
        warningCount: orchidGroups.filter((group) =>
          ["주의", "이상", "병해충"].includes(group.status),
        ).length,
        repotDueCount: 0,
        latestWorkDate: null,
        physicalBeds: house.physicalBeds,
      };
    }),
    orchidGroups: [],
  };
}

export function getOrchidManagementViewport(
  startBedId: number | null,
  bedCount: 2 | 3 | 4,
) {
  const params = new URLSearchParams({ bedCount: String(bedCount) });
  if (startBedId) {
    params.set("startBedId", String(startBedId));
  }
  return fetchApi<OrchidManagementViewport>(
    `/farm-status/orchid-management?${params.toString()}`,
  );
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

export function getWorkHistory(
  scopeType: "HOUSE" | "PHYSICAL_BED" | "BED_ZONE" | "ORCHID_GROUP",
  scopeId: number,
  page: number,
  size: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    scopeType,
    scopeId: String(scopeId),
    page: String(page),
    size: String(size),
  });
  return fetchApi<WorkHistoryPage>(`/work-history?${params.toString()}`, {
    signal,
  });
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

export async function createOrchidWorkOperation(
  payload: WorkRecordQuickPayload,
  workTypeName: string,
): Promise<WorkOperation> {
  const response = await fetch(`${API_BASE_URL}/work-operations/record`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      workTypeId: payload.workTypeId,
      title: `${workTypeName} 작업`,
      plannedStartDate: payload.workDate,
      sourceScopeType: payload.targetType,
      sourceScopeId: payload.targetId,
      sourceOrchidGroupIds: payload.targetIds,
      details: {
        materialName: payload.materialName,
        dilutionRatio: payload.dilutionRatio,
        quantity: payload.quantity,
      },
      worker: payload.worker,
      memo: payload.memo,
    }),
  });
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(
      resolveErrorMessage(body, "작업 이력을 저장하지 못했습니다."),
    );
  }
  return (body as { data: WorkOperation }).data;
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
