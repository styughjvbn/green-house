import { API_BASE_URL, fetchApi } from "@/shared/api/client";
import type {
  ConnectedOrchidGroup,
  InboundPottingPayload,
  InventoryPageResult,
  InboundRecord,
  InboundRecordPayload,
  InboundRecordUpdatePayload,
  Material,
  MaterialPayload,
  Variety,
  VarietyPayload,
} from "../model/types";

type ApiSuccess<T> = {
  data: T;
  message: string | null;
};

type ApiFailure = {
  error?: {
    message?: string;
    details?: string[];
  };
};

type VarietyResponse = {
  id: number;
  code: string;
  genus: string;
  name: string;
  alias: string | null;
  defaultPotSize: string | null;
  saleEnabled: boolean;
  active: boolean;
  description: string | null;
  memo: string | null;
  connectedGroupCount: number;
  totalQuantity: number;
  saleableQuantity: number;
  recentInboundDate: string | null;
  recentWorkDate: string | null;
  createdAt: string;
  updatedAt: string;
};

type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

type VarietyConnectedGroupResponse = {
  orchidGroupId: number;
  location: string;
  quantity: number;
  status: "정상" | "주의" | "이상";
  latestWorkDate: string | null;
};

type InboundRecordResponse = {
  id: number;
  inboundDate: string;
  inboundType: InboundRecord["inboundType"];
  varietyId: number;
  genus: string;
  varietyName: string;
  status: InboundRecord["status"];
  bottleCount: number | null;
  estimatedQuantity: number | null;
  actualQuantity: number | null;
  tempLocation: string | null;
  pottingDueDate: string | null;
  pottingDate: string | null;
  potSize: string | null;
  ageYear: number | null;
  growthStage: string | null;
  placementType: string | null;
  trayCount: number | null;
  bedZoneId: number | null;
  currentLocation: string | null;
  createdOrchidGroupId: number | null;
  worker: string | null;
  memo: string | null;
  createdAt: string;
  updatedAt: string;
};

type MaterialResponse = {
  id: number;
  code: string;
  category: Material["category"];
  name: string;
  manufacturer: string | null;
  specification: string | null;
  stockQuantity: string | null;
  storageLocation: string | null;
  usage: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

async function requestJson<T>(
  path: string,
  init: RequestInit,
  fallbackMessage: string,
) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: "include",
  });
  const payload = (await response.json()) as ApiSuccess<T> | ApiFailure;

  if (!response.ok) {
    const apiError = "error" in payload ? payload.error : undefined;
    throw new Error(
      apiError?.details?.find(Boolean) ?? apiError?.message ?? fallbackMessage,
    );
  }

  return (payload as ApiSuccess<T>).data;
}

type VarietyQuery = {
  keyword?: string;
  genus?: string;
  saleEnabled?: boolean;
  active?: boolean;
  page?: number;
  size?: number;
};

type MaterialQuery = {
  keyword?: string;
  category?: string;
  manufacturer?: string;
  active?: boolean;
  page?: number;
  size?: number;
};

type InboundQuery = {
  from?: string;
  to?: string;
  inboundType?: InboundRecord["inboundType"];
  status?: InboundRecord["status"];
  variety?: string;
  page?: number;
  size?: number;
};

export function getVarieties(query: VarietyQuery = {}) {
  return fetchApi<PageResponse<VarietyResponse>>(
    `/varieties${toQueryString(query)}`,
  ).then((result) => mapPage(result, toVariety));
}

export function getVarietyGenera() {
  return fetchApi<string[]>("/varieties/genera");
}

export function getVarietyOrchidGroups(varietyId: number) {
  return fetchApi<VarietyConnectedGroupResponse[]>(
    `/varieties/${varietyId}/orchid-groups`,
  ).then((items) =>
    items.map(
      (item): ConnectedOrchidGroup => ({
        id: item.orchidGroupId,
        location: item.location,
        quantity: item.quantity,
        status: item.status,
        latestWork: item.latestWorkDate,
      }),
    ),
  );
}

export function createVariety(payload: VarietyPayload) {
  return requestJson<VarietyResponse>(
    "/varieties",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "품종을 등록하지 못했습니다.",
  ).then(toVariety);
}

export function updateVariety(varietyId: number, payload: VarietyPayload) {
  return requestJson<VarietyResponse>(
    `/varieties/${varietyId}`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "품종을 수정하지 못했습니다.",
  ).then(toVariety);
}

export function deactivateVariety(varietyId: number) {
  return requestJson<VarietyResponse>(
    `/varieties/${varietyId}/deactivate`,
    {
      method: "PATCH",
    },
    "품종을 비활성화하지 못했습니다.",
  ).then(toVariety);
}

export function deleteVariety(varietyId: number) {
  return requestJson<null>(
    `/varieties/${varietyId}`,
    {
      method: "DELETE",
    },
    "품종을 삭제하지 못했습니다.",
  );
}

export function getMaterials(query: MaterialQuery = {}) {
  return fetchApi<PageResponse<MaterialResponse>>(
    `/materials${toQueryString(query)}`,
  ).then((result) => mapPage(result, toMaterial));
}

export function createMaterial(payload: MaterialPayload) {
  return requestJson<MaterialResponse>(
    "/materials",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "자재를 등록하지 못했습니다.",
  ).then(toMaterial);
}

export function updateMaterial(materialId: number, payload: MaterialPayload) {
  return requestJson<MaterialResponse>(
    `/materials/${materialId}`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "자재를 수정하지 못했습니다.",
  ).then(toMaterial);
}

export function deactivateMaterial(materialId: number) {
  return requestJson<MaterialResponse>(
    `/materials/${materialId}/deactivate`,
    {
      method: "PATCH",
    },
    "자재를 비활성화하지 못했습니다.",
  ).then(toMaterial);
}

export function deleteMaterial(materialId: number) {
  return requestJson<null>(
    `/materials/${materialId}`,
    {
      method: "DELETE",
    },
    "자재를 삭제하지 못했습니다.",
  );
}

export function getInboundRecords(query: InboundQuery = {}) {
  return fetchApi<PageResponse<InboundRecordResponse>>(
    `/inbound-records${toQueryString(query)}`,
  ).then((result) => mapPage(result, toInboundRecord));
}

export function createInboundRecord(payload: InboundRecordPayload) {
  return requestJson<InboundRecordResponse>(
    "/inbound-records",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "입고 기록을 등록하지 못했습니다.",
  ).then(toInboundRecord);
}

export function updateInboundRecord(
  inboundRecordId: number,
  payload: InboundRecordUpdatePayload,
) {
  return requestJson<InboundRecordResponse>(
    `/inbound-records/${inboundRecordId}`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "입고 기록을 수정하지 못했습니다.",
  ).then(toInboundRecord);
}

export function potInboundRecord(
  inboundRecordId: number,
  payload: InboundPottingPayload,
) {
  return requestJson<InboundRecordResponse>(
    `/inbound-records/${inboundRecordId}/potting`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    "포트 작업을 저장하지 못했습니다.",
  ).then(toInboundRecord);
}

export function cancelInboundRecord(inboundRecordId: number, memo?: string) {
  return requestJson<InboundRecordResponse>(
    `/inbound-records/${inboundRecordId}/cancel`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ memo: memo?.trim() || null }),
    },
    "입고 기록을 취소하지 못했습니다.",
  ).then(toInboundRecord);
}

export function deleteInboundRecord(inboundRecordId: number) {
  return requestJson<null>(
    `/inbound-records/${inboundRecordId}`,
    {
      method: "DELETE",
    },
    "입고 기록을 삭제하지 못했습니다.",
  );
}

function toVariety(item: VarietyResponse): Variety {
  return {
    id: item.id,
    code: item.code,
    genus: item.genus,
    name: item.name,
    alias: item.alias ?? "",
    potSize: item.defaultPotSize ?? "",
    saleEnabled: item.saleEnabled,
    status: item.active ? "ACTIVE" : "INACTIVE",
    description: item.description ?? "",
    memo: item.memo ?? "",
    registeredAt: item.createdAt.slice(0, 10),
    updatedAt: item.updatedAt.slice(0, 10),
    connectedGroupCount: item.connectedGroupCount,
    totalQuantity: item.totalQuantity,
    saleableQuantity: item.saleableQuantity,
    recentInboundDate: item.recentInboundDate,
    recentWorkDate: item.recentWorkDate,
    connectedGroups: [],
  };
}

function toInboundRecord(item: InboundRecordResponse): InboundRecord {
  return {
    id: item.id,
    inboundDate: item.inboundDate,
    inboundType: item.inboundType,
    varietyId: item.varietyId,
    genus: item.genus,
    varietyName: item.varietyName,
    status: item.status,
    bottleCount: item.bottleCount,
    estimatedQuantity: item.estimatedQuantity,
    actualQuantity: item.actualQuantity,
    tempLocation: item.tempLocation,
    pottingDueDate: item.pottingDueDate,
    pottingDate: item.pottingDate,
    potSize: item.potSize,
    ageYear: item.ageYear,
    growthStage: item.growthStage,
    placementType: item.placementType,
    trayCount: item.trayCount,
    bedZoneId: item.bedZoneId,
    currentLocation: item.currentLocation,
    createdOrchidGroupId: item.createdOrchidGroupId,
    worker: item.worker,
    memo: item.memo,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  };
}

function toMaterial(item: MaterialResponse): Material {
  return {
    id: item.id,
    code: item.code,
    category: item.category,
    name: item.name,
    manufacturer: item.manufacturer ?? "",
    specification: item.specification ?? "",
    stockQuantity: item.stockQuantity ?? "",
    storageLocation: item.storageLocation ?? "",
    usage: item.usage ?? "",
    status: item.active ? "ACTIVE" : "INACTIVE",
    registeredAt: item.createdAt.slice(0, 10),
    updatedAt: item.updatedAt.slice(0, 10),
  };
}

function mapPage<TSource, TTarget>(
  result: PageResponse<TSource> | TSource[],
  mapper: (item: TSource) => TTarget,
): InventoryPageResult<TTarget> {
  if (Array.isArray(result)) {
    return {
      content: result.map(mapper),
      page: 0,
      size: result.length,
      totalElements: result.length,
      totalPages: result.length ? 1 : 0,
    };
  }

  return {
    content: result.content.map(mapper),
    page: result.page,
    size: result.size,
    totalElements: result.totalElements,
    totalPages: result.totalPages,
  };
}

function toQueryString(
  query: Record<string, string | number | boolean | undefined>,
) {
  const params = new URLSearchParams();

  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    params.set(key, String(value));
  });

  const serialized = params.toString();
  return serialized ? `?${serialized}` : "";
}
