import type { FarmPlacementSelection } from "@/entities/farm/model/placement";
import type { AuctionFollowUpInput } from "./auctionFollowUpRequest";

export function buildAuctionArrivalInput(values: {
  recordOnly?: boolean;
  arrivalDate: string;
  varietyId: string;
  quantity: string;
  potSize: string;
  ageYear: string;
  status: string;
  placementType: string;
  trayCount: string;
  memo: string;
  worker: string;
  placement: FarmPlacementSelection | null;
}): AuctionFollowUpInput {
  if (values.recordOnly)
    return {
      operation: "ARRIVAL",
      payload: {
        arrivalDate: values.arrivalDate,
        quantity: Number(values.quantity),
        recordOnly: true,
        ...(values.worker.trim() ? { worker: values.worker.trim() } : {}),
      },
    };
  if (!values.placement)
    throw new Error("반환품을 배치할 논리 구역과 칸을 선택하세요.");
  if (!values.varietyId) throw new Error("반환품의 품종을 선택하세요.");
  return {
    operation: "ARRIVAL",
    payload: {
      arrivalDate: values.arrivalDate,
      bedZoneId: values.placement.bedZoneId,
      ...(values.worker.trim() ? { worker: values.worker.trim() } : {}),
      details: {
        varietyId: Number(values.varietyId),
        quantity: Number(values.quantity),
        potSize: values.potSize,
        status: values.status,
        placementType: values.placementType,
        ...(values.ageYear === "" ? {} : { ageYear: Number(values.ageYear) }),
        ...(values.trayCount === ""
          ? {}
          : { trayCount: Number(values.trayCount) }),
        startPosition: values.placement.startPosition,
        endPosition: values.placement.endPosition,
        splitPlacementAllowed: false,
        ...(values.memo.trim() ? { memo: values.memo.trim() } : {}),
      },
    },
  };
}
