import type { WorkRecord, WorkType, WorkTypeTemplate } from "./types";

export type WorkRecordField =
  | "materialName"
  | "dilutionRatio"
  | "quantity"
  | "worker"
  | "memo";

type WorkTypeTemplateConfig = {
  label: string;
  fields: WorkRecordField[];
  labels: Partial<Record<WorkRecordField, string>>;
};

const TEMPLATE_CONFIG: Record<WorkTypeTemplate, WorkTypeTemplateConfig> = {
  PESTICIDE: {
    label: "농약형",
    fields: ["materialName", "dilutionRatio", "quantity", "worker", "memo"],
    labels: {
      materialName: "약제명",
      dilutionRatio: "희석 배수",
      quantity: "사용량",
    },
  },
  FERTILIZER: {
    label: "비료형",
    fields: ["materialName", "quantity", "worker", "memo"],
    labels: {
      materialName: "비료/자재명",
      quantity: "사용량",
    },
  },
  REPOT: {
    label: "분갈이형",
    fields: ["quantity", "worker", "memo"],
    labels: {
      quantity: "작업 수량",
    },
  },
  CLEANUP: {
    label: "정리형",
    fields: ["quantity", "worker", "memo"],
    labels: {
      quantity: "작업 수량/범위",
    },
  },
  STATUS: {
    label: "상태 기록형",
    fields: ["worker", "memo"],
    labels: {},
  },
  MEMO: {
    label: "메모형",
    fields: ["worker", "memo"],
    labels: {},
  },
  MOVEMENT: {
    label: "자리 이동형",
    fields: ["worker", "memo"],
    labels: {},
  },
  MULTI_CREATE: {
    label: "난 묶음 다중 생성형",
    fields: ["worker", "memo"],
    labels: {},
  },
  CORRECTION: {
    label: "구조 변경 보정형",
    fields: ["worker", "memo"],
    labels: {},
  },
};

export const WORK_TYPE_TEMPLATES = Object.keys(
  TEMPLATE_CONFIG,
) as WorkTypeTemplate[];

const DEFAULT_FIELD_LABELS: Record<WorkRecordField, string> = {
  materialName: "자재명",
  dilutionRatio: "희석 배수",
  quantity: "수량",
  worker: "작업자",
  memo: "메모",
};

export function getWorkTypeTemplateConfig(template: WorkTypeTemplate | null) {
  return TEMPLATE_CONFIG[template ?? "MEMO"];
}

export function getWorkTypeTemplateLabel(template: WorkTypeTemplate | null) {
  return getWorkTypeTemplateConfig(template).label;
}

export function getWorkRecordFieldLabel(
  template: WorkTypeTemplate | null,
  field: WorkRecordField,
) {
  const config = getWorkTypeTemplateConfig(template);
  return config.labels[field] ?? DEFAULT_FIELD_LABELS[field];
}

export function isVisibleWorkRecordField(
  template: WorkTypeTemplate | null,
  field: WorkRecordField,
) {
  return getWorkTypeTemplateConfig(template).fields.includes(field);
}

export function getManualWorkTypes(workTypes: WorkType[]) {
  return workTypes.filter(
    (workType) =>
      workType.active &&
      !workType.systemType &&
      workType.code !== "INBOUND" &&
      workType.code !== "POTTING",
  );
}

export function findWorkType(workTypes: WorkType[], workTypeId: number | null) {
  return workTypes.find((workType) => workType.id === workTypeId) ?? null;
}

export function getRecordTemplate(
  record: WorkRecord,
  workTypes: WorkType[] = [],
) {
  return (
    record.workTypeTemplate ??
    findWorkType(workTypes, record.workTypeId)?.template ??
    null
  );
}

export function formatWorkRecordContent(
  record: WorkRecord,
  workTypes: WorkType[] = [],
) {
  const detailSummary = formatDetailsSummary(record);
  if (detailSummary) {
    return detailSummary;
  }

  const template = getRecordTemplate(record, workTypes);
  const fields = getWorkTypeTemplateConfig(template).fields.filter(
    (field) => field !== "memo" && field !== "worker",
  );
  const details = fields
    .map((field) => {
      const value = record[field];
      if (!value) return null;
      return `${getWorkRecordFieldLabel(template, field)} ${value}`;
    })
    .filter(Boolean);

  if (template === "MOVEMENT") {
    const from = record.fromBedZoneId
      ? `이전 구역 #${record.fromBedZoneId}`
      : null;
    const to = record.toBedZoneId ? `이동 구역 #${record.toBedZoneId}` : null;
    return [from, to].filter(Boolean).join(" -> ") || "자리 이동";
  }

  return details.join(" / ") || record.memo || "-";
}

function formatDetailsSummary(record: WorkRecord) {
  if (!record.details) {
    return null;
  }

  if (record.workType === "입고") {
    return [
      formatDetail(
        "입고 유형",
        formatInboundType(readDetail(record, "inboundType")),
      ),
      formatDetail("품종", readDetail(record, "varietyName")),
      formatInboundQuantity(record),
      formatDetail("화분", readDetail(record, "potSize")),
      formatDetail("임시 위치", readDetail(record, "tempLocation")),
    ]
      .filter(Boolean)
      .join(" / ");
  }

  if (record.workType === "포트 작업") {
    return [
      formatDetail("품종", readDetail(record, "varietyName")),
      formatDetail("수량", readDetail(record, "actualQuantity")),
      formatDetail("화분", readDetail(record, "potSize")),
    ]
      .filter(Boolean)
      .join(" / ");
  }

  return null;
}

function formatInboundQuantity(record: WorkRecord) {
  const bottleCount = readDetail(record, "bottleCount");
  if (bottleCount) {
    return formatDetail("수량", `${bottleCount}병`);
  }

  const actualQuantity = readDetail(record, "actualQuantity");
  if (actualQuantity) {
    return formatDetail("수량", `${actualQuantity}분`);
  }

  const estimatedQuantity = readDetail(record, "estimatedQuantity");
  if (estimatedQuantity) {
    return formatDetail("예상 수량", `${estimatedQuantity}분`);
  }

  return null;
}

function formatDetail(label: string, value: string | null) {
  return value ? `${label} ${value}` : null;
}

function readDetail(record: WorkRecord, key: string) {
  const value = record.details?.[key];
  if (value === null || value === undefined || value === "") {
    return null;
  }
  return String(value);
}

function formatInboundType(value: string | null) {
  if (!value) {
    return null;
  }

  return (
    {
      FLASK_SEEDLING: "유리병 모종",
      POTTED_SEEDLING: "포트 모종",
      PRODUCT_POT: "상품분",
      SAMPLE: "샘플",
      ETC: "기타",
    }[value] ?? value
  );
}
