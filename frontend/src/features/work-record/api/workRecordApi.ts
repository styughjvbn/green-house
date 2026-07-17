import { API_BASE_URL, fetchApi } from "@/shared/api/client";
import type {
  BedZone,
  OrchidGroup,
  PhysicalBed,
  WorkRecord,
  WorkOperation,
  WorkTargetPreview,
} from "@/entities/farm/types";
import type {
  CreateWorkRecordPayload,
  CreateWorkOperationPayload,
  InboundPottingCandidate,
  WorkOperationScopeOptions,
  WorkTargetSelectionOptions,
  WorkDerivedGroupOption,
  WorkCollectionOption,
  WorkTargetPreviewPayload,
  WorkRecordTargetOptions,
} from "../model/types";

export async function getWorkTargetGroupOptions(): Promise<{
  derivedGroups: WorkDerivedGroupOption[];
  collections: WorkCollectionOption[];
}> {
  const [derivedGroups, collections] = await Promise.all([
    fetchApi<WorkDerivedGroupOption[]>("/orchid-groups/derived-groups"),
    fetchApi<WorkCollectionOption[]>("/orchid-group-collections"),
  ]);
  return {
    derivedGroups,
    collections: collections.filter(
      (collection) => collection.status === "ACTIVE",
    ),
  };
}

export function getDerivedWorkTargetMembers(
  groupKey: string,
): Promise<OrchidGroup[]> {
  return fetchApi<OrchidGroup[]>(
    `/orchid-groups/derived-groups/${encodeURIComponent(groupKey)}/members`,
  );
}

export async function getWorkOperationScopeOptions(): Promise<WorkOperationScopeOptions> {
  const [derivedGroups, collections, orchidGroups] = await Promise.all([
    fetchApi<WorkOperationScopeOptions["derivedGroups"]>(
      "/orchid-groups/derived-groups",
    ),
    fetchApi<WorkOperationScopeOptions["collections"]>(
      "/orchid-group-collections",
    ),
    getSelectableWorkTargetGroups(),
  ]);
  return {
    derivedGroups,
    collections,
    orchidGroups,
  };
}

export async function getSelectableWorkTargetGroups(): Promise<OrchidGroup[]> {
  const groups = await fetchApi<OrchidGroup[]>("/orchid-groups");
  const excludedStatuses = new Set(["종료", "폐기", "판매 완료"]);
  return groups.filter(
    (group) => group.quantity > 0 && !excludedStatuses.has(group.status),
  );
}

export async function getWorkTargetSelectionOptions(): Promise<WorkTargetSelectionOptions> {
  const [orchidGroups, bedZones] = await Promise.all([
    getSelectableWorkTargetGroups(),
    fetchApi<BedZone[]>("/bed-zones"),
  ]);
  return {
    orchidGroups,
    bedZones: bedZones.filter((zone) => zone.active),
  };
}

export async function getWorkRecordTargetOptions(
  houseId: string,
): Promise<WorkRecordTargetOptions> {
  const [bedsResponse, zonesResponse, groupsResponse] = await Promise.all([
    fetch(`${API_BASE_URL}/physical-beds?houseId=${houseId}`, {
      cache: "no-store",
      credentials: "include",
    }),
    fetch(`${API_BASE_URL}/bed-zones?houseId=${houseId}`, {
      cache: "no-store",
      credentials: "include",
    }),
    fetch(`${API_BASE_URL}/orchid-groups?houseId=${houseId}`, {
      cache: "no-store",
      credentials: "include",
    }),
  ]);

  if (!bedsResponse.ok || !zonesResponse.ok || !groupsResponse.ok) {
    throw new Error("대상 목록을 불러오지 못했습니다.");
  }

  const [bedsPayload, zonesPayload, groupsPayload] = await Promise.all([
    bedsResponse.json(),
    zonesResponse.json(),
    groupsResponse.json(),
  ]);

  return {
    physicalBeds: (bedsPayload.data ?? []) as PhysicalBed[],
    bedZones: (zonesPayload.data ?? []) as BedZone[],
    orchidGroups: (groupsPayload.data ?? []) as OrchidGroup[],
  };
}

export async function createCompletedWorkOperationFromRecord(
  payload: CreateWorkRecordPayload,
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
      sourceScopeType: "MANUAL_SELECTION",
      sourceOrchidGroupIds: payload.orchidGroupIds,
      details: {
        materialName: payload.materialName,
        dilutionRatio: payload.dilutionRatio,
        quantity: payload.quantity,
      },
      worker: payload.worker,
      memo: payload.memo,
    }),
  });
  if (response.status === 404) {
    throw new Error(
      "난 묶음 다중 선택은 신규 작업 실행 기능이 활성화되어야 사용할 수 있습니다.",
    );
  }
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(body?.error?.message ?? "신규 작업을 처리하지 못했습니다.");
  }
  return body.data as WorkOperation;
}

export async function cancelWorkRecord({
  cancelReason,
  workRecordId,
}: {
  cancelReason: string | null;
  workRecordId: number;
}): Promise<WorkRecord> {
  const response = await fetch(
    `${API_BASE_URL}/work-records/${workRecordId}/cancel`,
    {
      method: "PATCH",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ cancelReason }),
    },
  );

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body?.error?.message ?? "작업 이력을 취소하지 못했습니다.");
  }

  return body.data as WorkRecord;
}

export async function previewWorkOperationTargets(
  payload: WorkTargetPreviewPayload,
): Promise<WorkTargetPreview> {
  return requestWorkOperation<WorkTargetPreview>(
    "/work-operations/target-preview",
    "POST",
    payload,
  );
}

export async function createWorkOperation(
  payload: CreateWorkOperationPayload,
): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    "/work-operations",
    "POST",
    payload,
  );
}

export function getInboundPottingCandidates(): Promise<
  InboundPottingCandidate[]
> {
  return fetchApi<InboundPottingCandidate[]>(
    "/work-operations/inbound-potting-candidates",
  );
}

export async function createInboundPottingPlan(payload: {
  title: string;
  plannedStartDate: string;
  plannedEndDate: string | null;
  inboundRecordIds: number[];
  worker: string | null;
  memo: string | null;
}): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    "/work-operations/inbound-potting-plans",
    "POST",
    payload,
  );
}

export function getWorkOperations(
  filters: {
    from?: string;
    to?: string;
    status?: WorkOperation["status"] | "";
    view?: "ALL" | "MANAGEMENT" | "HISTORY";
  } = {},
): Promise<WorkOperation[]> {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.status) params.set("status", filters.status);
  if (filters.view) params.set("view", filters.view);
  const query = params.toString();
  return fetchApi<WorkOperation[]>(
    `/work-operations${query ? `?${query}` : ""}`,
  );
}

export async function completeWorkOperation(
  workOperationId: number,
  completedDate: string,
): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    `/work-operations/${workOperationId}/complete`,
    "POST",
    { completedDate },
  );
}

export async function transitionWorkOperation(
  workOperationId: number,
  action: "start" | "pause" | "resume" | "cancel",
): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    `/work-operations/${workOperationId}/${action}`,
    "POST",
  );
}

export async function transitionWorkOperationTarget(
  workOperationId: number,
  targetId: number,
  action: "start" | "complete" | "skip",
  worker: string | null,
  resultDetails?: Record<string, unknown>,
  completedDate?: string,
): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    `/work-operations/${workOperationId}/targets/${targetId}/${action}`,
    "POST",
    { worker, resultDetails, completedDate },
  );
}

export async function completeMergeWorkOperation(
  workOperationId: number,
  worker: string | null,
  resultDetails: Record<string, unknown>,
  completedDate: string,
): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    `/work-operations/${workOperationId}/merge/complete`,
    "POST",
    { worker, resultDetails, completedDate },
  );
}

export async function executeStructureChangeWorkOperation(
  workOperationId: number,
  payload: {
    idempotencyKey: string;
    completedDate: string;
    worker: string | null;
    memo: string | null;
    sources: { sourceOrchidGroupId: number; inputQuantity: number }[];
    lossQuantity: number;
    lossReason: string | null;
    results: {
      bedZoneId: number;
      quantity: number;
      sourceOrchidGroupIds: number[];
      potSize: string | null;
      ageYear: number | null;
      purpose: "NORMAL" | "DIVIDE_CANDIDATE" | "HELD";
      placementType: null;
      trayCount: null;
      splitPlacementAllowed: false;
      startPosition: number;
      endPosition: number;
      memo: string | null;
    }[];
  },
): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    `/work-operations/${workOperationId}/structure-change-executions`,
    "POST",
    payload,
  );
}

async function requestWorkOperation<T>(
  path: string,
  method: "POST",
  payload?: unknown,
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    credentials: "include",
    headers: payload ? { "Content-Type": "application/json" } : undefined,
    body: payload ? JSON.stringify(payload) : undefined,
  });
  const body = await response.json().catch(() => null);

  if (!response.ok) {
    if (response.status === 404) {
      throw new Error("신규 작업 실행 기능이 비활성화되어 있습니다.");
    }
    throw new Error(body?.error?.message ?? "신규 작업을 처리하지 못했습니다.");
  }

  return body.data as T;
}
