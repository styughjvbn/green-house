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
  WorkOperationScopeOptions,
  WorkTargetPreviewPayload,
  WorkRecordTargetOptions,
} from "../model/types";

export async function getWorkOperationScopeOptions(): Promise<WorkOperationScopeOptions> {
  const [derivedGroups, collections, orchidGroups] = await Promise.all([
    fetchApi<WorkOperationScopeOptions["derivedGroups"]>(
      "/orchid-groups/derived-groups",
    ),
    fetchApi<WorkOperationScopeOptions["collections"]>(
      "/orchid-group-collections",
    ),
    fetchApi<OrchidGroup[]>("/orchid-groups"),
  ]);
  const excludedStatuses = new Set(["종료", "폐기", "판매 완료"]);
  return {
    derivedGroups,
    collections,
    orchidGroups: orchidGroups.filter(
      (group) => group.quantity > 0 && !excludedStatuses.has(group.status),
    ),
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

export async function createWorkRecord(
  payload: CreateWorkRecordPayload,
): Promise<WorkRecord> {
  const response = await fetch(`${API_BASE_URL}/work-records`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  const body = await response.json();

  if (!response.ok) {
    throw new Error(body?.error?.message ?? "작업 이력을 저장하지 못했습니다.");
  }

  return body.data as WorkRecord;
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

export async function completeWorkOperation(
  workOperationId: number,
): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    `/work-operations/${workOperationId}/complete`,
    "POST",
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
): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    `/work-operations/${workOperationId}/targets/${targetId}/${action}`,
    "POST",
    { worker },
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
