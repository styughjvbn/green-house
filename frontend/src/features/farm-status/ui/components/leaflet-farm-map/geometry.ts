import type {
  BedZone,
  FarmStatusZoomData,
  HouseStatusSummary,
  OrchidGroup,
  PhysicalBed,
} from "@/entities/farm/types";
import type { LatLngBoundsExpression, LatLngExpression } from "leaflet";
import type { SelectedFarmStatusOrchidGroup } from "../../../model/types";
import { HOUSE_WIDTH, WORLD_HEIGHT, WORLD_WIDTH } from "./config";

export type Rect = {
  x: number;
  y: number;
  width: number;
  height: number;
};

export type FarmHouseGeometry = Rect & {
  houseNumber: number;
};

export type VisualBedGeometry = Rect & {
  number: number;
};

export type VisualZoneGeometry = Rect & {
  side: "LEFT" | "RIGHT" | string;
};

export type DetailSource = {
  isReal: boolean;
  physicalBeds: PhysicalBed[];
};

export function getHouseDetailSource(
  house: HouseStatusSummary,
  zoomData: FarmStatusZoomData | null,
): DetailSource {
  const houseWithOptionalBeds = house as HouseStatusSummary & {
    physicalBeds?: PhysicalBed[];
  };

  if (Array.isArray(houseWithOptionalBeds.physicalBeds)) {
    return {
      isReal: true,
      physicalBeds: sortPhysicalBeds(houseWithOptionalBeds.physicalBeds),
    };
  }

  if (zoomData?.houseId === house.houseId) {
    return {
      isReal: true,
      physicalBeds: sortPhysicalBeds(zoomData.physicalBeds),
    };
  }

  return {
    isReal: false,
    physicalBeds: createVisualOnlyBeds(house.houseId, house.houseNumber),
  };
}

export function findOrchidGroupCenter(
  orchidGroupId: number,
  houseLayout: FarmHouseGeometry[],
  houseByNumber: Map<number, HouseStatusSummary>,
  zoomData: FarmStatusZoomData | null,
) {
  for (const geometry of houseLayout) {
    const house = houseByNumber.get(geometry.houseNumber);
    if (!house) continue;

    const detailSource = getHouseDetailSource(house, zoomData);
    const bedGeometries = getBedGeometries(geometry, detailSource.physicalBeds);

    for (const { bed, rect } of bedGeometries) {
      const zoneGeometries = getZoneGeometries(rect, bed.bedZones);

      for (const { zone, zoneRect } of zoneGeometries) {
        const orchidGeometry = getOrchidBlockGeometries(
          zoneRect,
          zone,
          bed,
        ).find(({ group }) => group.id === orchidGroupId);

        if (orchidGeometry) {
          return {
            x: orchidGeometry.rect.x + orchidGeometry.rect.width / 2,
            y: orchidGeometry.rect.y + orchidGeometry.rect.height / 2,
          };
        }
      }
    }
  }

  return null;
}

export function getScaledHouseLayout(
  houses: HouseStatusSummary[],
): FarmHouseGeometry[] {
  const orderedHouses = [...houses].sort(
    (a, b) => b.houseNumber - a.houseNumber,
  );
  const lengthByHouse = new Map<number, number>();

  orderedHouses.forEach((house) => {
    lengthByHouse.set(house.houseNumber, getMaxPhysicalBedLength(house));
  });

  const values = [...lengthByHouse.values()];
  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const minHeight = 360;
  const maxHeight = 650;
  const houseBottom = 810;

  const horizontalGap = 2;
  const totalWidth =
    orderedHouses.length * HOUSE_WIDTH +
    Math.max(0, orderedHouses.length - 1) * horizontalGap;
  const startX = (WORLD_WIDTH - totalWidth) / 2;

  return orderedHouses.map((house, index) => {
    const value = lengthByHouse.get(house.houseNumber) ?? minValue;
    const ratio =
      maxValue === minValue ? 0.5 : (value - minValue) / (maxValue - minValue);
    const height = Math.round(minHeight + ratio * (maxHeight - minHeight));

    return {
      houseNumber: house.houseNumber,
      x: startX + index * (HOUSE_WIDTH + horizontalGap),
      width: HOUSE_WIDTH,
      y: houseBottom - height,
      height,
    };
  });
}

function getMaxPhysicalBedLength(house: HouseStatusSummary | undefined) {
  const values =
    house?.physicalBeds
      .map((bed) => Number(bed.positionUnitCount ?? bed.lengthCm ?? 0))
      .filter((value) => Number.isFinite(value) && value > 0) ?? [];

  return Math.max(1, ...values);
}

function sortPhysicalBeds(beds: PhysicalBed[]) {
  return [...beds].sort((a, b) => {
    const aOrder =
      (a as PhysicalBed & { displayOrder?: number }).displayOrder ?? a.number;
    const bOrder =
      (b as PhysicalBed & { displayOrder?: number }).displayOrder ?? b.number;
    return aOrder - bOrder;
  });
}

function createVisualOnlyBeds(
  houseId: number,
  houseNumber: number,
): PhysicalBed[] {
  return [1, 2, 3].map((bedNumber) => ({
    id: -1 * (houseId * 10 + bedNumber),
    houseId,
    houseNumber,
    number: bedNumber,
    displayOrder: bedNumber,
    lengthCm: null,
    widthCm: null,
    wireCount: null,
    supportIntervalCm: null,
    positionUnitCount: bedNumber === 3 ? 28 : 24,
    positionUnitLabel: "칸",
    memo: null,
    bedZones: [
      {
        id: -1 * (houseId * 100 + bedNumber * 10 + 1),
        physicalBedId: -1 * (houseId * 10 + bedNumber),
        physicalBedNumber: bedNumber,
        houseId,
        houseNumber,
        name: String(bedNumber) + "다이 좌",
        side: "LEFT",
        zoneType: "DEFAULT",
        sortOrder: 1,
        active: true,
        memo: null,
        orchidGroups: [],
      },
      {
        id: -1 * (houseId * 100 + bedNumber * 10 + 2),
        physicalBedId: -1 * (houseId * 10 + bedNumber),
        physicalBedNumber: bedNumber,
        houseId,
        houseNumber,
        name: String(bedNumber) + "다이 우",
        side: "RIGHT",
        zoneType: "DEFAULT",
        sortOrder: 2,
        active: true,
        memo: null,
        orchidGroups: [],
      },
    ],
  }));
}

export function getBedGeometries(
  houseRect: FarmHouseGeometry,
  beds: PhysicalBed[],
): Array<{ bed: PhysicalBed; rect: VisualBedGeometry }> {
  const paddingX = 3;
  const topPadding = 34;
  const bottomPadding = 24;
  const gap = 3;
  const visibleBeds = beds.slice(0, 3);
  const availableWidth =
    houseRect.width - paddingX * 2 - gap * Math.max(0, visibleBeds.length - 1);
  const availableHeight = houseRect.height - topPadding - bottomPadding;
  const widthValues = getRelativeValues(visibleBeds, (bed) => bed.widthCm);
  const lengthValues = getRelativeValues(
    visibleBeds,
    (bed) => bed.lengthCm ?? bed.positionUnitCount,
  );
  const totalWidthValue = widthValues.reduce((sum, value) => sum + value, 0);
  const maxLengthValue = Math.max(...lengthValues, 1);
  let cursorRight = houseRect.x + houseRect.width - paddingX;

  return visibleBeds.map((bed, index) => {
    const width = (availableWidth * widthValues[index]) / totalWidthValue;
    const height = (availableHeight * lengthValues[index]) / maxLengthValue;
    const x = cursorRight - width;
    cursorRight = x - gap;

    return {
      bed,
      rect: {
        number: bed.number,
        x,
        y: houseRect.y + topPadding + (availableHeight - height),
        width,
        height,
      },
    };
  });
}

function getRelativeValues(
  beds: PhysicalBed[],
  readValue: (bed: PhysicalBed) => number | null,
) {
  const rawValues = beds.map((bed) => {
    const value = Number(readValue(bed) ?? 0);
    return Number.isFinite(value) && value > 0 ? value : null;
  });
  const fallback =
    rawValues.find((value): value is number => value !== null) ?? 1;

  return rawValues.map((value) => value ?? fallback);
}

export function getZoneGeometries(
  bedRect: VisualBedGeometry,
  zones: BedZone[],
): Array<{ zone: BedZone; zoneRect: VisualZoneGeometry }> {
  const gap = 2;
  const zoneWidth = (bedRect.width - gap) / 2;
  const sortedZones = [...zones].sort((a, b) => {
    const sideOrder = (side: string) =>
      side === "RIGHT" ? 0 : side === "LEFT" ? 1 : 2;
    return sideOrder(a.side) - sideOrder(b.side);
  });

  return sortedZones.slice(0, 2).map((zone, index) => ({
    zone,
    zoneRect: {
      side: zone.side,
      x: bedRect.x + index * (zoneWidth + gap),
      y: bedRect.y + 8,
      width: zoneWidth,
      height: bedRect.height - 16,
    },
  }));
}

export function getOrchidBlockGeometries(
  zoneRect: VisualZoneGeometry,
  zone: BedZone,
  bed: PhysicalBed,
) {
  const groups = [...zone.orchidGroups].sort((a, b) => {
    const aStart = a.startPosition ?? a.sortOrder;
    const bStart = b.startPosition ?? b.sortOrder;
    return aStart - bStart;
  });
  const positionedGroups = groups.filter(
    (group) => group.startPosition != null && group.endPosition != null,
  );
  const unpositionedGroups = groups.filter(
    (group) => group.startPosition == null || group.endPosition == null,
  );
  const gap = 3;
  const top = zoneRect.y + 34;
  const availableHeight = zoneRect.height - 44;
  const maxCell = Math.max(1, Math.floor(bed.positionUnitCount ?? 28));
  const cellHeight = availableHeight / maxCell;
  const positionedBlocks = positionedGroups.map((group) => {
    const range = resolveGroupCellRange({
      startPosition: group.startPosition,
      endPosition: group.endPosition,
      maxCell,
    });
    const height = Math.max(
      6,
      (range.endCell - range.startCell + 1) * cellHeight - gap,
    );

    return {
      group,
      rect: {
        x: zoneRect.x + 2,
        y: top + (range.startCell - 1) * cellHeight,
        width: zoneRect.width - 4,
        height,
      },
    };
  });
  const fallbackHeight = Math.max(
    18,
    (availableHeight - gap * Math.max(0, unpositionedGroups.length - 1)) /
      Math.max(1, unpositionedGroups.length),
  );
  const fallbackBlocks = unpositionedGroups.map((group, index) => ({
    group,
    rect: {
      x: zoneRect.x + 2,
      y: top + index * (fallbackHeight + gap),
      width: zoneRect.width - 4,
      height: Math.min(fallbackHeight, availableHeight),
    },
  }));

  return [...positionedBlocks, ...fallbackBlocks];
}

function resolveGroupCellRange({
  endPosition,
  maxCell,
  startPosition,
}: {
  endPosition: number | null | undefined;
  maxCell: number;
  startPosition: number | null | undefined;
}) {
  const startCell = clampCell(Math.floor(startPosition ?? 0) + 1, 1, maxCell);
  const endCell = clampCell(
    Math.ceil(endPosition ?? startPosition ?? startCell),
    1,
    maxCell,
  );

  return {
    startCell: Math.min(startCell, endCell),
    endCell: Math.max(startCell, endCell),
  };
}

function clampCell(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

export function toFarmStatusItem(
  group: OrchidGroup,
  zone: BedZone,
): SelectedFarmStatusOrchidGroup {
  return {
    ageYear: group.ageYear,
    orchidGroupId: group.id,
    varietyName: group.varietyName,
    genus: group.genus,
    endPosition: group.endPosition,
    memo: group.memo,
    placementType: group.placementType,
    potSize: group.potSize,
    quantity: group.quantity,
    sortOrder: group.sortOrder,
    splitPlacementAllowed: group.splitPlacementAllowed,
    startPosition: group.startPosition,
    status: group.status,
    trayCount: group.trayCount,
    varietyId: group.varietyId,
    houseId: group.houseId,
    houseNumber: group.houseNumber,
    physicalBedId: zone.physicalBedId,
    physicalBedNumber: group.physicalBedNumber,
    physicalBedName: `${group.physicalBedNumber}다이`,
    bedZoneId: zone.id,
    bedZoneName: zone.name,
  };
}

export function boundsOf(rect: Rect): LatLngBoundsExpression {
  const rotated = rotateRect(rect);
  const top = WORLD_HEIGHT - rotated.y;
  const bottom = WORLD_HEIGHT - rotated.y - rotated.height;

  return [
    [bottom, rotated.x],
    [top, rotated.x + rotated.width],
  ];
}

export function toLatLng(point: { x: number; y: number }): LatLngExpression {
  const rotated = rotatePoint(point);
  return [WORLD_HEIGHT - rotated.y, rotated.x];
}

export function toPolyline(
  points: Array<{ x: number; y: number }>,
): LatLngExpression[] {
  return points.map(toLatLng);
}

function rotatePoint(point: { x: number; y: number }) {
  return {
    x: WORLD_WIDTH - point.x,
    y: WORLD_HEIGHT - point.y,
  };
}

function rotateRect(rect: Rect): Rect {
  return {
    x: WORLD_WIDTH - rect.x - rect.width,
    y: WORLD_HEIGHT - rect.y - rect.height,
    width: rect.width,
    height: rect.height,
  };
}
