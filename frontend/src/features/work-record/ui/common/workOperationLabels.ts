import type { WorkOperation } from "@/entities/farm/types";
import { getWorkTypeDefinition } from "../../model/work-types/workTypeDefinition";

export function workPlanGuidance(code?: string) {
  return getWorkTypeDefinition(code ? { code } : null).planGuidance;
}

export function operationStatusLabel(status: WorkOperation["status"]) {
  return {
    PLANNED: "계획",
    IN_PROGRESS: "진행 중",
    PAUSED: "일시중지",
    COMPLETED: "완료",
    CANCELED: "취소",
    CORRECTED: "보정",
  }[status];
}

export function targetStatusLabel(
  status: WorkOperation["targets"][number]["executionStatus"],
) {
  return {
    PENDING: "대기",
    IN_PROGRESS: "진행 중",
    PARTIALLY_COMPLETED: "부분 완료",
    COMPLETED: "완료",
    SKIPPED: "건너뜀",
    CANCELED: "취소",
    FAILED: "실패",
  }[status];
}
