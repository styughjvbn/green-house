import type { WorkType } from "@/entities/farm/types";

export type WorkWorkflowKind =
  | "GENERIC"
  | "MOVEMENT"
  | "DISCARD"
  | "POTTING"
  | "STRUCTURE_CHANGE";

export type WorkExecutionKind =
  | "MOVEMENT"
  | "DISCARD"
  | "POTTING"
  | "STRUCTURE_CHANGE";

export type WorkRecordResultKind = "DISCARD" | "POTTING" | "STRUCTURE_CHANGE";

export type WorkTypeDefinition = {
  execution: WorkExecutionKind | null;
  planGuidance: string;
  recordResult: WorkRecordResultKind | null;
  targetSource: "ORCHID_GROUP" | "INBOUND_RECORD";
  workflow: WorkWorkflowKind;
};

const GENERIC_DEFINITION: WorkTypeDefinition = {
  execution: null,
  planGuidance:
    "난 묶음을 계획 대상으로 확정하고, 작업 유형에 맞는 기록 내용을 저장합니다.",
  recordResult: null,
  targetSource: "ORCHID_GROUP",
  workflow: "GENERIC",
};

const SPECIAL_DEFINITIONS: Record<string, WorkTypeDefinition> = {
  MOVEMENT: {
    execution: "MOVEMENT",
    planGuidance:
      "원본 난 묶음을 계획 대상으로 확정하고, 실행할 때 각 묶음의 목적 구역과 위치를 입력합니다.",
    recordResult: null,
    targetSource: "ORCHID_GROUP",
    workflow: "MOVEMENT",
  },
  DISCARD: {
    execution: "DISCARD",
    planGuidance:
      "폐기할 난 묶음을 대상으로 정하고, 실행할 때 대상별 일부 또는 전량 폐기 수량과 사유를 입력합니다.",
    recordResult: "DISCARD",
    targetSource: "ORCHID_GROUP",
    workflow: "DISCARD",
  },
  POTTING: {
    execution: "POTTING",
    planGuidance:
      "포트 작업 대기 입고 기록을 선택하고, 실행할 때 실제 수량과 배치 위치를 입력합니다.",
    recordResult: "POTTING",
    targetSource: "INBOUND_RECORD",
    workflow: "POTTING",
  },
  REPOT: structureChangeDefinition(
    "보통 자동·사용자 그룹 하나를 대상으로 정하고, 실행 회차마다 작업한 일부 수량과 여러 결과 묶음을 기록합니다.",
  ),
  DIVIDE: structureChangeDefinition(
    "대상 그룹을 정한 뒤 실행 회차마다 작업한 일부 수량과 여러 결과 묶음을 기록합니다.",
  ),
  MERGE: structureChangeDefinition(
    "같은 품종의 대상 그룹을 정하고, 실행 회차마다 작업한 일부 수량과 여러 결과 묶음을 기록합니다.",
  ),
};

export function getWorkTypeDefinition(
  workType: Pick<WorkType, "code"> | null | undefined,
): WorkTypeDefinition {
  return workType
    ? (SPECIAL_DEFINITIONS[workType.code] ?? GENERIC_DEFINITION)
    : GENERIC_DEFINITION;
}

export function getWorkExecutionKind(code: string): WorkExecutionKind | null {
  return SPECIAL_DEFINITIONS[code]?.execution ?? null;
}

function structureChangeDefinition(planGuidance: string): WorkTypeDefinition {
  return {
    execution: "STRUCTURE_CHANGE",
    planGuidance,
    recordResult: "STRUCTURE_CHANGE",
    targetSource: "ORCHID_GROUP",
    workflow: "STRUCTURE_CHANGE",
  };
}
