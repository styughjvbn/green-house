import type {
  FarmStatusOrchidGroupList,
  FarmZoomLevel,
  HouseStatusSummary,
} from "@/entities/farm/types";

export const farmStatusZoomOrder: FarmZoomLevel[] = [
  "FARM",
  "HOUSE",
  "PHYSICAL_BED",
  "BED_ZONE",
];

export function getNextZoomLevel(current: FarmZoomLevel): FarmZoomLevel {
  const currentIndex = farmStatusZoomOrder.indexOf(current);
  return farmStatusZoomOrder[
    Math.min(currentIndex + 1, farmStatusZoomOrder.length - 1)
  ];
}

export function getPreviousZoomLevel(current: FarmZoomLevel): FarmZoomLevel {
  const currentIndex = farmStatusZoomOrder.indexOf(current);
  return farmStatusZoomOrder[Math.max(currentIndex - 1, 0)];
}

export function zoomLabel(level: FarmZoomLevel): string {
  return {
    FARM: "전체",
    HOUSE: "동",
    PHYSICAL_BED: "다이",
    BED_ZONE: "구역",
  }[level];
}

export function selectionTitle(
  selection: FarmStatusOrchidGroupList | null,
  selectedHouse: HouseStatusSummary | null,
): string {
  if (selection) {
    return `${selection.targetName} 난 묶음 목록`;
  }
  if (selectedHouse) {
    return `${selectedHouse.houseName} 난 묶음 목록`;
  }
  return "난 묶음 목록";
}

export function hasHouseWarning(house: HouseStatusSummary | null): boolean {
  return Boolean(house && (house.warningCount > 0 || house.repotDueCount > 0));
}
