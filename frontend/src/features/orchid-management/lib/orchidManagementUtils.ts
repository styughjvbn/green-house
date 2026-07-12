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

export function resolveMaxCell(house: House, bedZoneId: number | null) {
  const bed = house.physicalBeds.find((item) =>
    item.bedZones.some((zone) => zone.id === bedZoneId),
  );
  return Math.max(1, Math.floor(bed?.positionUnitCount ?? 28));
}

export function normalizeCellRange(
  startCell: string,
  endCell: string,
  maxCell: number,
) {
  const start = parseCell(startCell);
  const end = parseCell(endCell);
  const fallback = start ?? end ?? 1;
  const first = clampCell(start ?? fallback, 1, maxCell);
  const last = clampCell(end ?? first, 1, maxCell);
  return {
    startCell: Math.min(first, last),
    endCell: Math.max(first, last),
  };
}

export function resolveGroupCellRange({
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

export function buildOccupiedCells(
  orchidGroups: OrchidGroup[],
  excludeOrchidGroupId: number | null,
  maxCell: number,
) {
  const cells = new Set<number>();
  orchidGroups.forEach((orchidGroup) => {
    if (orchidGroup.id === excludeOrchidGroupId) {
      return;
    }
    const range = resolveGroupCellRange({
      startPosition: orchidGroup.startPosition,
      endPosition: orchidGroup.endPosition,
      maxCell,
    });
    for (let cell = range.startCell; cell <= range.endCell; cell += 1) {
      cells.add(cell);
    }
  });
  return cells;
}

export function clampCellRangeToAvailable({
  endCell,
  occupiedCells,
  startCell,
}: {
  endCell: number;
  occupiedCells: Set<number>;
  startCell: number;
}) {
  if (occupiedCells.has(startCell)) {
    return null;
  }
  if (startCell === endCell) {
    return { startCell, endCell };
  }

  const direction = endCell > startCell ? 1 : -1;
  let lastAvailable = startCell;
  for (
    let cell = startCell + direction;
    direction > 0 ? cell <= endCell : cell >= endCell;
    cell += direction
  ) {
    if (occupiedCells.has(cell)) {
      break;
    }
    lastAvailable = cell;
  }

  return {
    startCell: Math.min(startCell, lastAvailable),
    endCell: Math.max(startCell, lastAvailable),
  };
}

export function normalizeAvailableCellRange({
  endCell,
  maxCell,
  occupiedCells,
  startCell,
}: {
  endCell: string;
  maxCell: number;
  occupiedCells: Set<number>;
  startCell: string;
}) {
  const parsedStart = parseCell(startCell);
  const parsedEnd = parseCell(endCell);
  const fallback = parsedStart ?? parsedEnd ?? 1;
  const rawStart = clampCell(parsedStart ?? fallback, 1, maxCell);
  const rawEnd = clampCell(parsedEnd ?? rawStart, 1, maxCell);
  const requestedStart = rawStart;
  const requestedEnd = rawEnd < requestedStart ? requestedStart : rawEnd;
  const start = occupiedCells.has(requestedStart)
    ? findNearestAvailableCell(requestedStart, occupiedCells, maxCell)
    : requestedStart;
  if (start == null) {
    return { startCell: requestedStart, endCell: requestedEnd };
  }
  return (
    clampCellRangeToAvailable({
      startCell: start,
      endCell: Math.max(start, requestedEnd),
      occupiedCells,
    }) ?? { startCell: start, endCell: start }
  );
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

function parseCell(value: string) {
  const cell = Number(value);
  if (!Number.isFinite(cell)) {
    return null;
  }
  return Math.floor(cell);
}

function clampCell(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function findNearestAvailableCell(
  cell: number,
  occupiedCells: Set<number>,
  maxCell: number,
) {
  for (let current = cell + 1; current <= maxCell; current += 1) {
    if (!occupiedCells.has(current)) {
      return current;
    }
  }
  for (let current = cell - 1; current >= 1; current -= 1) {
    if (!occupiedCells.has(current)) {
      return current;
    }
  }
  return null;
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
