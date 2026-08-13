import type { WorkType, WorkTypeWorkflow } from "@/entities/farm/types";

type WorkWorkflowKind =
  | "GENERIC"
  | "MOVEMENT"
  | "DISCARD"
  | "POTTING"
  | "STRUCTURE_CHANGE";

type WorkExecutionKind = Exclude<WorkTypeWorkflow, "GENERIC">;

export type WorkRecordResultKind =
  | "DISCARD"
  | "MOVEMENT"
  | "POTTING"
  | "STRUCTURE_CHANGE";

type WorkTypeDefinition = {
  category: "GENERAL" | "STRUCTURE_CHANGE" | null;
  execution: WorkExecutionKind | null;
  planGuidance: string;
  planSupported: boolean;
  recordSupported: boolean;
  recordResult: WorkRecordResultKind | null;
  targetSource: "ORCHID_GROUP" | "INBOUND_RECORD";
  workflow: WorkWorkflowKind;
};

const PLAN_GUIDANCE: Record<string, string> = {
  MOVEMENT:
    "원본 난 묶음을 계획 대상으로 확정하고, 실행할 때 각 묶음의 목적 구역과 위치를 입력합니다.",
  DISCARD:
    "폐기할 난 묶음을 대상으로 정하고, 실행할 때 대상별 일부 또는 전량 폐기 수량과 사유를 입력합니다.",
  POTTING:
    "포트 작업 대기 입고 기록을 선택하고, 실행할 때 실제 수량과 배치 위치를 입력합니다.",
  REPOT:
    "보통 자동·사용자 그룹 하나를 대상으로 정하고, 실행 회차마다 작업한 일부 수량과 여러 결과 묶음을 기록합니다.",
  DIVIDE:
    "대상 그룹을 정한 뒤 실행 회차마다 작업한 일부 수량과 여러 결과 묶음을 기록합니다.",
  MERGE:
    "같은 품종의 대상 그룹을 정하고, 실행 회차마다 작업한 일부 수량과 여러 결과 묶음을 기록합니다.",
};

const DEFAULT_PLAN_GUIDANCE =
  "난 묶음을 계획 대상으로 확정하고, 작업 유형에 맞는 기록 내용을 저장합니다.";

export function getWorkTypeDefinition(
  workType:
    | Pick<WorkType, "code" | "registrationModes" | "targetSource" | "workflow">
    | null
    | undefined,
): WorkTypeDefinition {
  const workflow = workType?.workflow ?? "GENERIC";
  const execution = getWorkExecutionKind(workflow);
  return {
    category: workType
      ? workflow === "GENERIC"
        ? "GENERAL"
        : "STRUCTURE_CHANGE"
      : null,
    execution,
    planGuidance: getWorkPlanGuidance(workType?.code),
    planSupported: workType?.registrationModes.includes("PLAN") ?? false,
    recordSupported: workType?.registrationModes.includes("RECORD") ?? false,
    recordResult: execution,
    targetSource: workType?.targetSource ?? "ORCHID_GROUP",
    workflow,
  };
}

export function getWorkExecutionKind(
  workflow: WorkTypeWorkflow,
): WorkExecutionKind | null {
  return workflow === "GENERIC" ? null : workflow;
}

export function getWorkPlanGuidance(code?: string) {
  return (code && PLAN_GUIDANCE[code]) || DEFAULT_PLAN_GUIDANCE;
}
