import type { House } from "@/entities/farm/types";
import type {
  InboundRecord,
  InboundStatus,
  InboundType,
  InboundRecordUpdatePayload,
} from "../model/types";

export const INBOUND_TYPE_LABELS: Record<InboundType, string> = {
  FLASK_SEEDLING: "유리병 모종",
  POTTED_SEEDLING: "포트 모종",
  PRODUCT_POT: "상품분",
  SAMPLE: "샘플",
  ETC: "기타",
};

export const INBOUND_STATUS_LABELS: Record<InboundStatus, string> = {
  TEMP_STORED: "임시보관",
  POTTING_PENDING: "포트작업대기",
  POTTED: "포트작업완료",
  PLACED: "배치완료",
  CANCELED: "취소",
};

export function createInboundEditForm(
  record: InboundRecord,
): InboundRecordUpdatePayload {
  return {
    inboundDate: record.inboundDate,
    estimatedQuantity: record.estimatedQuantity ?? undefined,
    actualQuantity: record.actualQuantity ?? undefined,
    tempLocation: record.tempLocation ?? undefined,
    pottingDueDate: record.pottingDueDate ?? undefined,
    potSize: record.potSize ?? undefined,
    ageYear: record.ageYear ?? undefined,
    growthStage: record.growthStage ?? undefined,
    placementType: record.placementType ?? undefined,
    trayCount: record.trayCount ?? undefined,
    worker: record.worker ?? undefined,
    memo: record.memo ?? undefined,
  };
}

export function flattenZones(houses: House[]) {
  return houses.flatMap((house) =>
    house.physicalBeds.flatMap((bed) =>
      bed.bedZones.map((zone) => ({
        id: zone.id,
        label: `${house.number}동 ${bed.number}다이 ${zone.name}`,
      })),
    ),
  );
}

export function toNumber(value: string) {
  if (!value.trim()) return undefined;
  return Number(value);
}

export function toOptionalNumber(value: string) {
  if (!value.trim()) return undefined;
  return Number(value);
}

export function setQueryParam(
  params: URLSearchParams,
  key: string,
  value: FormDataEntryValue | null,
  emptyValue = "",
) {
  const normalized = typeof value === "string" ? value.trim() : "";

  if (!normalized || normalized === emptyValue) {
    params.delete(key);
    return;
  }

  params.set(key, normalized);
}
