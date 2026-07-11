import type {
  BedZone,
  House,
  OrchidGroup,
  PhysicalBed,
} from "@/entities/farm/types";

export function nullableText(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export function nullableNumber(value: string): number | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? Number(trimmed) : null;
}

export function positionToStartCell(value: number | null | undefined): string {
  return value == null ? "" : formatPositionCell(value + 1);
}

export function positionToEndCell(value: number | null | undefined): string {
  return value == null ? "" : formatPositionCell(value);
}

export function startCellToPosition(value: string): number | null {
  const cell = nullableNumber(value);
  return cell == null ? null : cell - 1;
}

export function endCellToPosition(value: string): number | null {
  return nullableNumber(value);
}

export function formatCellRange({
  endPosition,
  startPosition,
}: {
  endPosition: number | null | undefined;
  startPosition: number | null | undefined;
}) {
  const startCell = positionToStartCell(startPosition);
  const endCell = positionToEndCell(endPosition);

  if (!startCell || !endCell) {
    return null;
  }

  return `${startCell}-${endCell}칸`;
}

function formatPositionCell(value: number) {
  return Number.isInteger(value) ? String(value) : String(value);
}

export function findFirstOrchidGroup(house: House): OrchidGroup | null {
  for (const bed of house.physicalBeds) {
    for (const zone of bed.bedZones) {
      if (zone.orchidGroups[0]) {
        return zone.orchidGroups[0];
      }
    }
  }
  return null;
}

export function findOrchidGroup(
  house: House,
  orchidGroupId: number,
): OrchidGroup | null {
  for (const bed of house.physicalBeds) {
    for (const zone of bed.bedZones) {
      const orchidGroup = zone.orchidGroups.find(
        (item) => item.id === orchidGroupId,
      );
      if (orchidGroup) {
        return orchidGroup;
      }
    }
  }
  return null;
}

export function findBedZone(
  house: House,
  bedZoneId: number,
): { bed: PhysicalBed; zone: BedZone } | null {
  for (const bed of house.physicalBeds) {
    for (const zone of bed.bedZones) {
      if (zone.id === bedZoneId) {
        return { bed, zone };
      }
    }
  }
  return null;
}

export function findFirstBedZoneInPhysicalBed(
  house: House,
  physicalBedId: number,
): BedZone | null {
  const bed = house.physicalBeds.find((item) => item.id === physicalBedId);
  return bed?.bedZones[0] ?? null;
}

export function findFirstAvailableSingleSlot(
  house: House,
  bedZoneId: number,
): { startPosition: number; endPosition: number } | null {
  const resolved = findBedZone(house, bedZoneId);
  if (!resolved || resolved.bed.positionUnitCount == null) {
    return null;
  }

  const positionedGroups = [...resolved.zone.orchidGroups]
    .filter(
      (orchidGroup) =>
        orchidGroup.startPosition != null && orchidGroup.endPosition != null,
    )
    .sort((a, b) => {
      const startCompare = (a.startPosition ?? 0) - (b.startPosition ?? 0);
      return startCompare !== 0 ? startCompare : a.sortOrder - b.sortOrder;
    });

  let cursor = 0;
  for (const orchidGroup of positionedGroups) {
    const start = orchidGroup.startPosition ?? 0;
    const end = orchidGroup.endPosition ?? start;
    if (start - cursor >= 1) {
      return {
        startPosition: cursor + 1,
        endPosition: cursor + 1,
      };
    }
    if (end > cursor) {
      cursor = end;
    }
  }

  if ((resolved.bed.positionUnitCount ?? 0) - cursor >= 1) {
    return {
      startPosition: cursor + 1,
      endPosition: cursor + 1,
    };
  }

  return null;
}
