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
        const orchidGeometry = getOrchidBlockGeometries(zoneRect, zone).find(
          ({ group }) => group.id === orchidGroupId,
        );

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
  const bedWidth = (houseRect.width - paddingX * 2 - gap * 2) / 3;
  const bedHeight = houseRect.height - topPadding - bottomPadding;

  return beds.slice(0, 3).map((bed, index) => ({
    bed,
    rect: {
      number: bed.number,
      x: houseRect.x + paddingX + (2 - index) * (bedWidth + gap),
      y: houseRect.y + topPadding,
      width: bedWidth,
      height: bedHeight,
    },
  }));
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
) {
  const groups = [...zone.orchidGroups].sort((a, b) => {
    const aStart = a.startPosition ?? a.sortOrder;
    const bStart = b.startPosition ?? b.sortOrder;
    return aStart - bStart;
  });
  const gap = 3;
  const top = zoneRect.y + 34;
  const availableHeight = zoneRect.height - 44;
  const blockHeight = Math.max(
    18,
    (availableHeight - gap * Math.max(0, groups.length - 1)) /
      Math.max(1, groups.length),
  );

  return groups.map((group, index) => ({
    group,
    rect: {
      x: zoneRect.x + 2,
      y: top + index * (blockHeight + gap),
      width: zoneRect.width - 4,
      height: Math.min(blockHeight, availableHeight),
    },
  }));
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
