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
