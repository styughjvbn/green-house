import type { FarmStatusOrchidGroupList, FarmZoomLevel, HouseStatusSummary } from "@/entities/farm/types";

export function zoomLabel(level: FarmZoomLevel) {
  return {
    FARM: "전체",
    HOUSE: "동",
    PHYSICAL_BED: "배드",
    BED_ZONE: "구역",
  }[level];
}

export function selectionTitle(
  selection: FarmStatusOrchidGroupList | null,
  selectedHouse: HouseStatusSummary | null,
) {
  if (selection) {
    return `${selection.targetName} 난 묶음 목록`;
  }
  if (selectedHouse) {
    return `${selectedHouse.houseName} 난 묶음 목록`;
  }
  return "난 묶음 목록";
}
