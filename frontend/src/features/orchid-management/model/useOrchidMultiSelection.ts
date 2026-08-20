"use client";

import { useMemo, useState } from "react";
import type { House } from "@/entities/farm/types";

export function useOrchidMultiSelection(house: House) {
  const [enabled, setEnabled] = useState(false);
  const [selectedById, setSelectedById] = useState(
    () => new Map<number, ReturnType<typeof orchidGroupsFromHouse>[number]>(),
  );
  const orchidGroupsById = useMemo(
    () =>
      new Map(orchidGroupsFromHouse(house).map((group) => [group.id, group])),
    [house],
  );
  const selectedIds = useMemo(
    () => new Set(selectedById.keys()),
    [selectedById],
  );
  const selectedOrchidGroups = useMemo(
    () => Array.from(selectedById.values()).reverse(),
    [selectedById],
  );

  function toggleEnabled() {
    if (enabled) setSelectedById(new Map());
    setEnabled((current) => !current);
  }

  function toggleOrchidGroup(orchidGroupId: number) {
    const orchidGroup = orchidGroupsById.get(orchidGroupId);
    if (!orchidGroup) return;
    setSelectedById((current) => {
      const next = new Map(current);
      if (next.has(orchidGroupId)) next.delete(orchidGroupId);
      else next.set(orchidGroupId, orchidGroup);
      return next;
    });
  }

  return {
    enabled,
    selectedIds,
    selectedOrchidGroups,
    toggleEnabled,
    toggleOrchidGroup,
  };
}

function orchidGroupsFromHouse(house: House) {
  return house.physicalBeds.flatMap((bed) =>
    bed.bedZones.flatMap((zone) => zone.orchidGroups),
  );
}
