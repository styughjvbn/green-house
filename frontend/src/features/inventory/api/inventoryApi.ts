import { API_BASE_URL, fetchApi } from "@/shared/api/client";
import type {
  ConnectedOrchidGroup,
  InboundPottingPayload,
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
  const response = await fetch(`${API_BASE_URL}${path}`, init);
  const payload = (await response.json()) as ApiSuccess<T> | ApiFailure;

  if (!response.ok) {
    const apiError = "error" in payload ? payload.error : undefined;
    throw new Error(
      apiError?.details?.find(Boolean) ?? apiError?.message ?? fallbackMessage,
    );
  }

  return (payload as ApiSuccess<T>).data;
}

export function getVarieties() {
  return fetchApi<VarietyResponse[]>("/varieties").then((items) =>
    items.map(toVariety),
  );
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

export function getMaterials() {
  return fetchApi<MaterialResponse[]>("/materials").then((items) =>
    items.map(toMaterial),
  );
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

export function getInboundRecords() {
  return fetchApi<InboundRecordResponse[]>("/inbound-records").then((items) =>
    items.map(toInboundRecord),
  );
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
