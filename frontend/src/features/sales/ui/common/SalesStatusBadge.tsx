import type {
  AuctionLot,
  AuctionSettlementStatus,
} from "@/entities/farm/types";
import {
  auctionStatusLabel,
  auctionStatusTone,
} from "../../lib/auctionDisplay";

type StatusBadgeSize = "default" | "compact";

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
  const classes = {
    blue: "bg-[#e6f0ff] text-[#246df2]",
    green: "bg-[#e7f7e8] text-[#16853b]",
    orange: "bg-[#fff1d6] text-[#d88400]",
    gray: "bg-[#eef1ee] text-[#657169]",
  }[tone];

  return (
    <StatusPill className={classes} size={size}>
      {value}
    </StatusPill>
  );
}

export function AuctionLotStatusBadge({
  size = "default",
  status,
}: {
  size?: StatusBadgeSize;
  status: AuctionLot["currentStatus"];
}) {
  const tone = auctionStatusTone(status);
  const classes = {
    green: "bg-[#e5f5e8] text-[#16853b]",
    orange: "bg-[#fff1d8] text-[#c66f00]",
    red: "bg-[#fee9e7] text-[#c43d35]",
    blue: "bg-[#e9f1fb] text-[#286aa6]",
  }[tone];

  return (
    <StatusPill className={classes} size={size}>
      {auctionStatusLabel(status)}
    </StatusPill>
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
  const classes = warning
    ? "bg-[#fff0ed] text-[#c4473c]"
    : done
      ? "bg-[#e8f6ec] text-[#158442]"
      : "bg-[#fff5df] text-[#a96a00]";

  return (
    <StatusPill className={classes} size={size}>
      {settlementStatusLabels[status]}
    </StatusPill>
  );
}

function StatusPill({
  children,
  className,
  size,
}: {
  children: string;
  className: string;
  size: StatusBadgeSize;
}) {
  const sizeClass =
    size === "compact"
      ? "rounded px-1.5 py-0.5 text-[10px]"
      : "rounded px-2 py-1 text-[11px]";

  return (
    <span
      className={`inline-flex font-bold whitespace-nowrap ${sizeClass} ${className}`}
    >
      {children}
    </span>
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
