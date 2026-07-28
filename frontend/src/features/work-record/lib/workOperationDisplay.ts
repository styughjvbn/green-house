import type { WorkOperation } from "@/entities/farm/types";

export function workOperationScopeLabel(operation: WorkOperation) {
  const label = {
    NONE: "대상 없음",
    FARM: "농장 전체",
    HOUSE: "동",
    PHYSICAL_BED: "다이",
    BED_ZONE: "논리 구역",
    ORCHID_GROUP: "난 묶음",
    DERIVED_GROUP: "자동 그룹",
    USER_COLLECTION: "사용자 그룹",
    MANUAL_SELECTION: "직접 선택",
    INBOUND_RECORD_SELECTION: "입고 포트 대상",
  }[operation.sourceScopeType];

  return operation.sourceScopeId == null
    ? label
    : `${label} #${operation.sourceScopeId}`;
}
