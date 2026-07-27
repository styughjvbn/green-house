import { API_BASE_URL, fetchApi } from "@/shared/api/client";
import type {
  BedZone,
  OrchidGroup,
  WorkOperation,
  WorkTargetPreview,
} from "@/entities/farm/types";
import type { Page } from "@/shared/api/page";
import type {
  CompletedWorkOperationPayload,
  CreateWorkOperationPayload,
  InboundPottingCandidate,
  WorkOperationScopeOptions,
  WorkTargetSelectionOptions,
  WorkDerivedGroupOption,
  WorkCollectionOption,
  WorkTargetPreviewPayload,
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

export function getOrchidGroups(): Promise<OrchidGroup[]> {
  return fetchApi<OrchidGroup[]>("/orchid-groups");
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

export async function createCompletedWorkOperation(
  payload: CompletedWorkOperationPayload,
  workTypeName: string,
  title?: string | null,
): Promise<WorkOperation> {
  const response = await fetch(`${API_BASE_URL}/work-operations/record`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      workTypeId: payload.workTypeId,
      title: title?.trim() || `${workTypeName} 작업`,
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

export async function createWorkOperationsBatch(
  payload: CreateWorkOperationPayload,
): Promise<WorkOperation[]> {
  return requestWorkOperation<WorkOperation[]>(
    "/work-operations/batch",
    "POST",
    { operation: payload },
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

export async function createInboundPottingPlansBatch(payload: {
  title: string;
  plannedStartDate: string;
  plannedEndDate: string | null;
  inboundRecordIds: number[];
  worker: string | null;
  memo: string | null;
}): Promise<WorkOperation[]> {
  return requestWorkOperation<WorkOperation[]>(
    "/work-operations/inbound-potting-plans/batch",
    "POST",
    { plan: payload },
  );
}

export type WorkOperationQuery = {
  from?: string;
  to?: string;
  status?: WorkOperation["status"] | "";
  view?: "ALL" | "MANAGEMENT" | "HISTORY";
  scopeType?: WorkOperation["sourceScopeType"];
  scopeId?: number;
  keyword?: string;
  page?: number;
  size?: number;
};

export function getWorkOperations(
  filters: WorkOperationQuery = {},
): Promise<Page<WorkOperation>> {
  const params = new URLSearchParams();
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.status) params.set("status", filters.status);
  if (filters.view) params.set("view", filters.view);
  if (filters.scopeType) params.set("scopeType", filters.scopeType);
  if (filters.scopeId != null) params.set("scopeId", String(filters.scopeId));
  if (filters.keyword?.trim()) params.set("keyword", filters.keyword.trim());
  if (filters.page != null) params.set("page", String(filters.page));
  if (filters.size != null) params.set("size", String(filters.size));
  const query = params.toString();
  return fetchApi<Page<WorkOperation>>(
    `/work-operations${query ? `?${query}` : ""}`,
  );
}

export async function getAllWorkOperations(
  filters: Omit<WorkOperationQuery, "page" | "size"> = {},
): Promise<WorkOperation[]> {
  const size = 100;
  const firstPage = await getWorkOperations({ ...filters, page: 0, size });
  const remainingPages = await Promise.all(
    Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, index) =>
      getWorkOperations({ ...filters, page: index + 1, size }),
    ),
  );
  return [
    ...firstPage.content,
    ...remainingPages.flatMap((page) => page.content),
  ];
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

export async function executeStructureChangeWorkOperation(
  workOperationId: number,
  payload: StructureChangeExecutionPayload,
): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    `/work-operations/${workOperationId}/structure-change-executions`,
    "POST",
    payload,
  );
}

export type StructureChangeExecutionPayload = {
  idempotencyKey: string;
  completedDate: string;
  worker: string | null;
  memo: string | null;
  sources: {
    sourceOrchidGroupId: number;
    inputQuantity: number;
    releasedStartPosition: number | null;
    releasedEndPosition: number | null;
  }[];
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
};

export type StructureChangeRecordPayload = {
  operation: CreateWorkOperationPayload;
  execution: StructureChangeExecutionPayload;
};

export function createStructureChangeRecord(
  payload: StructureChangeRecordPayload,
): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    "/work-operations/structure-change-records",
    "POST",
    payload,
  );
}

export function createStructureChangeRecords(
  records: StructureChangeRecordPayload[],
): Promise<WorkOperation[]> {
  return requestWorkOperation<WorkOperation[]>(
    "/work-operations/structure-change-records/batch",
    "POST",
    { records },
  );
}

export function createDiscardRecord(payload: {
  operation: CreateWorkOperationPayload;
  completedDate: string;
  worker: string | null;
  results: Array<{
    orchidGroupId: number;
    discardQuantity: number;
    reason: string | null;
  }>;
}): Promise<WorkOperation> {
  return requestWorkOperation<WorkOperation>(
    "/work-operations/discard-records",
    "POST",
    payload,
  );
}

export type InboundPottingExecutionPayload = {
  inboundRecordId: number;
  pottingDate: string;
  results: Array<{
    bedZoneId: number;
    quantity: number;
    potSize?: string;
    ageYear?: number;
    placementType?: string;
    trayCount?: number;
    splitPlacementAllowed: boolean;
    startPosition: number;
    endPosition: number;
    memo?: string;
  }>;
  growthStage?: string;
  worker?: string;
  memo?: string;
};

export function createInboundPottingRecord(payload: {
  plan: {
    title: string;
    plannedStartDate: string;
    plannedEndDate: string | null;
    inboundRecordIds: number[];
    worker: string | null;
    memo: string | null;
  };
  executions: InboundPottingExecutionPayload[];
}): Promise<WorkOperation[]> {
  return requestWorkOperation<WorkOperation[]>(
    "/work-operations/inbound-potting-records",
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
