import type { MutationGraphLocation } from "../model/types";

export function formatMutationGraphLocation(
  location?: MutationGraphLocation | null,
) {
  if (!location) return "위치 정보 없음";
  const hierarchy = hierarchyLabel(location);
  const range = closedCellRange(location.startPosition, location.endPosition);
  if (hierarchy && range) return `${hierarchy}, ${range}`;
  return hierarchy ?? range ?? "위치 정보 없음";
}

export function closedCellRange(
  startPosition?: number | null,
  endPosition?: number | null,
) {
  if (startPosition == null || endPosition == null) return null;
  const startCell = Math.floor(startPosition) + 1;
  const endCell = Math.ceil(endPosition);
  if (!Number.isFinite(startCell) || !Number.isFinite(endCell)) return null;
  return `${startCell}~${Math.max(startCell, endCell)}칸`;
}

function hierarchyLabel(location: MutationGraphLocation) {
  if (location.houseNumber == null || location.physicalBedNumber == null) {
    return location.bedZoneName ?? null;
  }
  return `${location.houseNumber}동 ${location.physicalBedNumber}다이 ${sideLabel(location)}`;
}

function sideLabel(location: MutationGraphLocation) {
  switch (location.side) {
    case "LEFT":
      return "좌측";
    case "RIGHT":
      return "우측";
    case "HANGING":
      return "행잉";
    case "CUSTOM":
      return location.bedZoneName ?? "사용자 구역";
    default:
      return location.bedZoneName ?? "구역";
  }
}
