export const TYPE_LABELS: Record<string, string> = {
  BASELINE_IMPORT: "기준 상태 이관",
  CREATE: "생성",
  UPDATE_DETAILS: "상세 수정",
  MOVE: "이동",
  RESERVE: "판매 예약",
  RELEASE_RESERVATION: "예약 해제",
  CONSUME_RESERVATION: "출고",
  RESTORE_OUTBOUND: "출고 복구",
  DISCARD: "폐기",
  TRANSFORM: "구조 변경",
  CORRECTION: "보정",
  COMPENSATION: "보상",
  CANCEL_CREATION: "생성 취소",
  DELETE: "삭제",
};

export const DOMAIN_LABELS: Record<string, string> = {
  FARM: "농장",
  WORK: "작업",
  SALES: "판매",
  INBOUND: "입고",
  MIGRATION: "이관",
};

export function mutationTypeColor(type: string) {
  if (["CORRECTION", "COMPENSATION", "RESTORE_OUTBOUND"].includes(type)) {
    return "bg-[#f59e0b]";
  }
  if (["DELETE", "DISCARD", "CANCEL_CREATION"].includes(type)) {
    return "bg-[#dc5b4d]";
  }
  if (["CREATE", "BASELINE_IMPORT"].includes(type)) return "bg-[#3182ce]";
  return "bg-[#159447]";
}
