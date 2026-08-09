"use client";

import { useMemo, useState } from "react";
import type { House } from "@/entities/farm/types";

export function useOrchidMultiSelection(house: House) {
  const [enabled, setEnabled] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(() => new Set());
  const orchidGroupsById = useMemo(
    () =>
      new Map(
        house.physicalBeds.flatMap((bed) =>
          bed.bedZones.flatMap((zone) =>
            zone.orchidGroups.map(
              (orchidGroup) => [orchidGroup.id, orchidGroup] as const,
            ),
          ),
        ),
      ),
    [house],
  );
  const selectedOrchidGroups = useMemo(
    () =>
      Array.from(selectedIds)
        .reverse()
        .map((orchidGroupId) => orchidGroupsById.get(orchidGroupId))
        .filter((orchidGroup) => orchidGroup != null),
    [orchidGroupsById, selectedIds],
  );

  function toggleEnabled() {
    if (enabled) setSelectedIds(new Set());
    setEnabled((current) => !current);
  }

  function toggleOrchidGroup(orchidGroupId: number) {
    if (!orchidGroupsById.has(orchidGroupId)) return;
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(orchidGroupId)) next.delete(orchidGroupId);
      else next.add(orchidGroupId);
      return next;
    });
  }

  function clear() {
    setSelectedIds(new Set());
  }

  return {
    clear,
    enabled,
    selectedIds,
    selectedOrchidGroups,
    toggleEnabled,
    toggleOrchidGroup,
  };
}
