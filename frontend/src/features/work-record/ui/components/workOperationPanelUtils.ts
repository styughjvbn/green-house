import type { WorkOperation } from "@/entities/farm/types";
import type {
  WorkOperationFormState,
  WorkTargetPreviewPayload,
} from "../../model/types";

export function workPlanGuidance(code?: string) {
  switch (code) {
    case "MOVEMENT":
      return "원본 난 묶음을 계획 대상으로 확정하고, 실행할 때 각 묶음의 목적 구역과 위치를 입력합니다.";
    case "REPOT":
      return "보통 자동·사용자 그룹 하나를 대상으로 정하고, 실행 회차마다 작업한 일부 수량과 여러 결과 묶음을 기록합니다.";
    case "DIVIDE":
      return "대상 그룹을 정한 뒤 실행 회차마다 작업한 일부 수량과 여러 결과 묶음을 기록합니다.";
    case "MERGE":
      return "같은 품종의 대상 그룹을 정하고, 실행 회차마다 작업한 일부 수량과 여러 결과 묶음을 기록합니다.";
    case "POTTING":
      return "포트 작업 대기 입고 기록을 선택하고, 실행할 때 실제 수량과 배치 위치를 입력합니다.";
    case "DISCARD":
      return "폐기할 난 묶음을 대상으로 정하고, 실행할 때 대상별 일부 또는 전량 폐기 수량과 사유를 입력합니다.";
    default:
      return "난 묶음을 계획 대상으로 확정하고, 작업 유형에 맞는 기록 내용을 저장합니다.";
  }
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

export function buildScopePayload(
  form: WorkOperationFormState,
  manualIds: Set<number>,
): WorkTargetPreviewPayload | null {
  switch (form.sourceScopeType) {
    case "FARM":
      return { scopeType: "FARM" };
    case "HOUSE":
      return form.houseId
        ? { scopeType: "HOUSE", scopeId: Number(form.houseId) }
        : null;
    case "DERIVED_GROUP":
      return form.scopeKey
        ? { scopeType: "DERIVED_GROUP", scopeKey: form.scopeKey }
        : null;
    case "USER_COLLECTION":
      return form.collectionId
        ? {
            scopeType: "USER_COLLECTION",
            scopeId: Number(form.collectionId),
          }
        : null;
    case "MANUAL_SELECTION":
      return manualIds.size > 0
        ? {
            scopeType: "MANUAL_SELECTION",
            orchidGroupIds: [...manualIds],
          }
        : null;
    case "INBOUND_RECORD_SELECTION":
      return null;
  }
}
