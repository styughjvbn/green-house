import type { WorkOperation } from "@/entities/farm/types";
import type {
  WorkOperationFormState,
  WorkTargetPreviewPayload,
} from "../../model/types";
import { getWorkTypeDefinition } from "../../model/workTypeDefinition";

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
