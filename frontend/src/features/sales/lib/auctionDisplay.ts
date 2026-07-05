import type {
  AuctionInspectionStatus,
  AuctionLotStatus,
} from "@/entities/farm/types";

const statusLabels: Record<AuctionLotStatus, string> = {
  SHIPPED: "출하완료",
  WAITING: "경매대기",
  IN_PROGRESS: "경매진행",
  SOLD: "판매완료",
  PARTIALLY_SOLD: "부분판매",
  FAILED: "유찰",
  REAUCTION_WAITING: "재경매대기",
  RETURN_INFERRED: "반환추정",
  PARTIALLY_RETURNED: "부분반환",
  RETURNED: "반환완료",
  QUANTITY_MISMATCH: "수량불일치",
  REVIEW_REQUIRED: "확인필요",
  CANCELLED: "취소",
};

const inspectionLabels: Record<AuctionInspectionStatus, string> = {
  NORMAL: "정상",
  AUTO_MATCHED: "자동매칭",
  CORRECTED_MATCH: "보정매칭",
  MANUAL_REVIEW: "수동확인",
  MATCH_FAILED: "매칭불가",
  QUANTITY_MISMATCH: "수량불일치",
  RETURN_INFERRED: "반환추정",
  SOURCE_ERROR: "원본오류",
};

const attemptStatusLabels: Record<string, string> = {
  SOLD: "낙찰",
  FAILED: "유찰",
  PARTIALLY_SOLD: "부분 낙찰",
  RETURN_INFERRED: "반환 추정",
};

export const auctionStatusOptions = Object.entries(statusLabels) as Array<
  [AuctionLotStatus, string]
>;

export function auctionStatusLabel(status: AuctionLotStatus) {
  return statusLabels[status];
}

export function auctionInspectionLabel(status: AuctionInspectionStatus) {
  return inspectionLabels[status];
}

export function auctionAttemptStatusLabel(status: string) {
  return attemptStatusLabels[status] ?? status;
}

export function auctionStatusTone(status: AuctionLotStatus) {
  if (["SOLD", "RETURNED"].includes(status)) return "green";
  if (
    [
      "RETURN_INFERRED",
      "PARTIALLY_RETURNED",
      "QUANTITY_MISMATCH",
      "REVIEW_REQUIRED",
    ].includes(status)
  )
    return "red";
  if (["FAILED", "REAUCTION_WAITING", "PARTIALLY_SOLD"].includes(status))
    return "orange";
  return "blue";
}
