import type { WorkType, WorkTypeTemplate } from "./types";

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
  DISCARD: {
    label: "폐기형",
    fields: ["quantity", "worker", "memo"],
    labels: {
      quantity: "폐기 수량",
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
      workType.workflow === "GENERIC" &&
      workType.registrationModes.includes("RECORD"),
  );
}

export function getSchedulableWorkTypes(workTypes: WorkType[]) {
  return workTypes.filter(
    (workType) => workType.active && workType.registrationModes.length > 0,
  );
}

export function findWorkType(workTypes: WorkType[], workTypeId: number | null) {
  return workTypes.find((workType) => workType.id === workTypeId) ?? null;
}
