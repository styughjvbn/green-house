import { fetchApi, requestApi } from "@/shared/api/client";
import type {
  BedZonePlacementProfile,
  House,
  OrchidManagementBedOrderItem,
  OrchidManagementViewport,
  OrchidGroup,
  VarietyOption,
  WorkOperation,
  WorkType,
} from "@/entities/farm/types";
import type {
  DerivedOrchidGroup,
  MutationPayload,
  OrchidGroupBatchUpdateItem,
  OrchidGroupCollection,
  OrchidGroupLineage,
  WorkOperationCorrections,
  WorkHistoryPage,
} from "../model/types";
import { createUuid } from "@/shared/lib/id";
import { movementRecordPayload } from "../lib/movementRecordPayload";

export function getOrchidGroupLineage(orchidGroupId: number) {
  return fetchApi<OrchidGroupLineage>(
    `/orchid-groups/${orchidGroupId}/lineage`,
  );
}

export function getOrchidManagementHouses(): Promise<House[]> {
  return fetchApi<House[]>("/houses");
}

export function reconcileOrchidGroup(
  orchidGroupId: number,
  payload: {
    idempotencyKey: string;
    title: string;
    workDate: string;
    worker: string | null;
    memo: string | null;
    reason: string;
    actualQuantity: number;
    actualStatus: string;
    actualBedZoneId: number;
    actualStartPosition: number;
    actualEndPosition: number;
  },
): Promise<WorkOperation> {
  return requestApi<WorkOperation>(
    `/orchid-groups/${orchidGroupId}/reconciliations`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "현장 상태를 동기화하지 못했습니다.",
  );
}

export function getWorkOperationCorrections(workOperationId: number) {
  return fetchApi<WorkOperationCorrections>(
    `/work-operations/${workOperationId}/corrections`,
  );
}

export async function createWorkOperationCorrection(
  workOperationId: number,
  payload: import("@/shared/api/generated/openapi").components["schemas"]["WorkOperationCorrectionCreateRequest"],
): Promise<WorkOperationCorrections> {
  return requestApi<WorkOperationCorrections>(
    `/work-operations/${workOperationId}/corrections`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "보정 작업을 완료하지 못했습니다.",
  );
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

export async function updateOrchidGroupsBatch(
  orchidGroups: OrchidGroupBatchUpdateItem[],
): Promise<void> {
  await requestApi<void>(
    "/orchid-groups/batch",
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ orchidGroups }),
    },
    "일괄 보정을 완료하지 못했습니다.",
  );
}

export async function deleteOrchidGroup(orchidGroupId: number): Promise<void> {
  await requestApi<void>(
    `/orchid-groups/${orchidGroupId}`,
    { method: "DELETE" },
    "삭제하지 못했습니다.",
  );
}

export async function recordOrchidGroupMovement(
  orchidGroupId: number,
  destination: {
    bedZoneId: number;
    startPosition: number;
    endPosition: number;
  },
  businessDate: string,
): Promise<void> {
  const [group, types] = await Promise.all([
    fetchApi<OrchidGroup>(`/orchid-groups/${orchidGroupId}`),
    fetchApi<WorkType[]>("/work-types"),
  ]);
  const movementType = types.find((type) => type.code === "MOVEMENT");
  if (!movementType) throw new Error("자리 이동 작업 유형을 찾을 수 없습니다.");
  await requestApi<void>(
    "/work-operations/structure-change-records/batch",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(
        movementRecordPayload(
          group,
          destination,
          movementType.id,
          businessDate,
          createUuid(),
        ),
      ),
    },
    "이동하지 못했습니다.",
  );
}

export function getBedZonePlacementProfile(bedZoneId: number) {
  return fetchApi<BedZonePlacementProfile>(
    `/bed-zones/${bedZoneId}/placement-profile`,
  );
}

export async function saveBedZonePlacementProfile(
  profile: BedZonePlacementProfile,
): Promise<BedZonePlacementProfile> {
  return requestApi<BedZonePlacementProfile>(
    `/bed-zones/${profile.bedZoneId}/placement-profile`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ capacities: profile.capacities }),
    },
    "다이 정밀 설정을 저장하지 못했습니다.",
  );
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

export function getOrchidManagementBedOrder() {
  return fetchApi<OrchidManagementBedOrderItem[]>(
    "/farm-status/orchid-management/bed-order",
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

export function getWorkHistory(
  historyScopeType: "HOUSE" | "PHYSICAL_BED" | "BED_ZONE" | "ORCHID_GROUP",
  historyScopeId: number,
  page: number,
  size: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    historyScopeType,
    historyScopeId: String(historyScopeId),
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

export function getDerivedOrchidGroups(houseId?: number | null) {
  const params = new URLSearchParams();
  if (houseId != null) {
    params.set("houseId", String(houseId));
  }
  const query = params.toString();
  return fetchApi<DerivedOrchidGroup[]>(
    `/orchid-groups/derived-groups${query ? `?${query}` : ""}`,
  );
}

export function getDerivedOrchidGroupMembers(
  groupKey: string,
  houseId?: number | null,
) {
  const params = new URLSearchParams();
  if (houseId != null) {
    params.set("houseId", String(houseId));
  }
  const query = params.toString();
  return fetchApi<OrchidGroup[]>(
    `/orchid-groups/derived-groups/${encodeURIComponent(groupKey)}/members${query ? `?${query}` : ""}`,
  );
}

export async function fetchHouse(houseId: number): Promise<House> {
  return requestApi<House>(
    `/houses/${houseId}`,
    {},
    "대상 동을 불러오지 못했습니다.",
  );
}

async function submitOrchidMutation(
  path: string,
  method: "POST" | "PATCH",
  payload: MutationPayload & { bedZoneId?: number },
): Promise<void> {
  await requestApi<void>(
    path,
    {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "저장하지 못했습니다.",
  );
}
