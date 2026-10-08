import type { AuctionLot } from "@/entities/farm/types";
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
