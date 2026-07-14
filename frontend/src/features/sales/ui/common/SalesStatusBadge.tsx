import type {
  AuctionLot,
  AuctionSettlementStatus,
} from "@/entities/farm/types";
import { StatusBadge, type StatusBadgeSize } from "@/shared/ui/StatusBadge";
import {
  auctionStatusLabel,
  auctionStatusTone,
} from "../../lib/auctionDisplay";

export function SalesSlipStatusBadge({
  size = "default",
  value,
}: {
  size?: StatusBadgeSize;
  value: string;
}) {
  const tone =
    value === "미입금"
      ? "orange"
      : value === "작성중"
        ? "blue"
        : value === "취소"
          ? "gray"
          : "green";

  return (
    <StatusBadge size={size} tone={tone}>
      {value}
    </StatusBadge>
  );
}

export function AuctionLotStatusBadge({
  size = "default",
  status,
}: {
  size?: StatusBadgeSize;
  status: AuctionLot["currentStatus"];
}) {
  return (
    <StatusBadge size={size} tone={auctionStatusTone(status)}>
      {auctionStatusLabel(status)}
    </StatusBadge>
  );
}

export function AuctionSettlementStatusBadge({
  size = "default",
  status,
}: {
  size?: StatusBadgeSize;
  status: AuctionSettlementStatus;
}) {
  const warning = ["AMOUNT_MISMATCH", "REVIEW_REQUIRED"].includes(status);
  const done = status === "PAID";

  return (
    <StatusBadge size={size} tone={warning ? "red" : done ? "green" : "orange"}>
      {settlementStatusLabels[status]}
    </StatusBadge>
  );
}

const settlementStatusLabels: Record<AuctionSettlementStatus, string> = {
  CREATED: "생성",
  PAYMENT_WAITING: "입금 대기",
  PARTIALLY_PAID: "부분 입금",
  PAID: "정산 완료",
  AMOUNT_MISMATCH: "금액 불일치",
  REVIEW_REQUIRED: "확인 필요",
  CANCELLED: "취소",
};
