"use client";

import type { PhysicalBed } from "@/entities/farm/types";
import type { OrchidSelection } from "../../model/types";
import BedZoneBlock from "./BedZoneBlock";

export default function PhysicalBedBlock({
  bed,
  selection,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  bed: PhysicalBed;
  selection: OrchidSelection | null;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  return (
    <div className="rounded-md border border-[#cfe0cc] bg-[#f7faf6] p-2">
      <h3 className="text-center text-lg font-semibold">{bed.number}배드</h3>
      <div className="mt-2 grid grid-cols-2 gap-2">
        {bed.bedZones.map((zone) => (
          <BedZoneBlock
            key={zone.id}
            zone={zone}
            selected={selection?.type === "BED_ZONE" && selection.bedZoneId === zone.id}
            selectedOrchidGroupId={selection?.type === "ORCHID_GROUP" ? selection.orchidGroupId : null}
            onSelectBedZone={onSelectBedZone}
            onSelectOrchidGroup={onSelectOrchidGroup}
          />
        ))}
      </div>
    </div>
  );
}

