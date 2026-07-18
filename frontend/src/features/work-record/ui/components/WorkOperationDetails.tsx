import type { WorkOperation } from "@/entities/farm/types";
import {
  getWorkRecordFieldLabel,
  isVisibleWorkRecordField,
} from "@/entities/farm/workTypes";

const DETAIL_LABELS: Record<string, string> = {
  actualQuantity: "실제 수량",
  ageYear: "년생",
  bottleCount: "병 수",
  dilutionRatio: "희석 배수",
  estimatedQuantity: "예상 수량",
  genus: "속명",
  growthStage: "생육 단계",
  inboundType: "입고 유형",
  materialName: "자재명",
  placementType: "배치 규격",
  potSize: "화분 크기",
  pottingDueDate: "포트 예정일",
  quantity: "사용량",
  status: "상태",
  tempLocation: "임시 위치",
  trayCount: "판수",
  varietyName: "품종",
};

const HIDDEN_DETAIL_KEYS = new Set([
  "inboundRecordId",
  "orchidGroupId",
  "requestKey",
  "idempotencyKey",
]);

export function WorkOperationDetails({
  operation,
}: {
  operation: WorkOperation;
}) {
  const details = Object.entries(operation.details ?? {})
    .filter(
      ([key, value]) =>
        !HIDDEN_DETAIL_KEYS.has(key) &&
        value !== null &&
        value !== undefined &&
        value !== "",
    )
    .map(([key, value]) => ({
      key,
      label: detailLabel(operation, key),
      value: formatDetailValue(value),
    }));

  const items = [
    { key: "workType", label: "작업 유형", value: operation.workType },
    ...details,
    ...(operation.worker
      ? [{ key: "worker", label: "작업자", value: operation.worker }]
      : []),
    ...(operation.memo
      ? [{ key: "memo", label: "메모", value: operation.memo }]
      : []),
  ];

  return (
    <dl className="mt-4 grid gap-x-5 gap-y-3 rounded-md border border-[#e1e6df] bg-[#fbfcfa] p-4 sm:grid-cols-2">
      {items.map((item) => (
        <div className="min-w-0" key={item.key}>
          <dt className="text-xs font-semibold text-[#6a766e]">{item.label}</dt>
          <dd className="mt-1 text-sm font-semibold break-words whitespace-pre-wrap text-[#26352b]">
            {item.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}

function detailLabel(operation: WorkOperation, key: string) {
  if (
    key === "materialName" &&
    isVisibleWorkRecordField(operation.workTypeTemplate, "materialName")
  ) {
    return getWorkRecordFieldLabel(operation.workTypeTemplate, "materialName");
  }
  if (
    key === "dilutionRatio" &&
    isVisibleWorkRecordField(operation.workTypeTemplate, "dilutionRatio")
  ) {
    return getWorkRecordFieldLabel(operation.workTypeTemplate, "dilutionRatio");
  }
  if (
    key === "quantity" &&
    isVisibleWorkRecordField(operation.workTypeTemplate, "quantity")
  ) {
    return getWorkRecordFieldLabel(operation.workTypeTemplate, "quantity");
  }
  return DETAIL_LABELS[key] ?? key;
}

function formatDetailValue(value: unknown): string {
  if (Array.isArray(value)) {
    return value.map(formatDetailValue).join(", ");
  }
  if (typeof value === "object" && value !== null) {
    return Object.entries(value)
      .map(
        ([key, nestedValue]) =>
          `${DETAIL_LABELS[key] ?? key}: ${formatDetailValue(nestedValue)}`,
      )
      .join(" / ");
  }
  if (typeof value === "boolean") {
    return value ? "예" : "아니오";
  }
  return String(value);
}
