"use client";

import { useEffect, useState } from "react";
import type { BedZonePlacementProfile, House } from "@/entities/farm/types";
import { getBedZonePlacementProfile } from "../../api/orchidManagementApi";
import type { DragState, OrchidSelection } from "../../model/types";
import PhysicalBedBlock from "./PhysicalBedBlock";

export default function HouseDetailMap({
  dragState,
  house,
  placementEditMode,
  saving,
  selection,
  onDragEnd,
  onDragStart,
  onDropOnBedZone,
  onEnterDropZone,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  dragState: DragState;
  house: House;
  placementEditMode: boolean;
  saving: boolean;
  selection: OrchidSelection | null;
  onDragEnd: () => void;
  onDragStart: (orchidGroupId: number) => void;
  onDropOnBedZone: (bedZoneId: number) => Promise<void>;
  onEnterDropZone: (bedZoneId: number) => void;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  const [profiles, setProfiles] = useState<
    Record<number, BedZonePlacementProfile>
  >({});

  useEffect(() => {
    let cancelled = false;

    async function loadProfiles() {
      const zoneIds = house.physicalBeds.flatMap((bed) =>
        bed.bedZones.map((zone) => zone.id),
      );
      const results = await Promise.allSettled(
        zoneIds.map(
          async (zoneId) =>
            [zoneId, await getBedZonePlacementProfile(zoneId)] as const,
        ),
      );

      if (cancelled) {
        return;
      }

      const nextProfiles: Record<number, BedZonePlacementProfile> = {};
      for (const result of results) {
        if (result.status === "fulfilled") {
          const [zoneId, profile] = result.value;
          nextProfiles[zoneId] = profile;
        }
      }
      setProfiles(nextProfiles);
    }

    void loadProfiles();

    return () => {
      cancelled = true;
    };
  }, [house]);

  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
      <div className="grid gap-3 xl:grid-cols-3">
        {house.physicalBeds.map((bed) => (
          <PhysicalBedBlock
            key={bed.id}
            bed={bed}
            profiles={profiles}
            dragState={dragState}
            placementEditMode={placementEditMode}
            saving={saving}
            selection={selection}
            onDragEnd={onDragEnd}
            onDragStart={onDragStart}
            onDropOnBedZone={onDropOnBedZone}
            onEnterDropZone={onEnterDropZone}
            onSelectBedZone={onSelectBedZone}
            onSelectOrchidGroup={onSelectOrchidGroup}
          />
        ))}
      </div>
    </section>
  );
}
