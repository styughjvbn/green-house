import { salesDispatchLabel } from "../../lib/salesDisplay";
import type { AuctionLot, SalesSlip } from "@/entities/farm/types";
import { StatusBadge, type StatusBadgeSize } from "@/shared/ui/StatusBadge";
import {
  auctionStatusLabel,
  auctionStatusTone,
} from "../../lib/auctionDisplay";

export function SalesSlipStatusBadge({
  size = "default",
  value,
  salesType,
}: {
  size?: StatusBadgeSize;
  value: string;
  salesType?: SalesSlip["salesType"];
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
      {salesDispatchLabel(value, salesType)}
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
