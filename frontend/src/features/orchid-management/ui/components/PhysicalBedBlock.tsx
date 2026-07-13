"use client";

import type { PhysicalBed } from "@/entities/farm/types";
import type { MapCellRangePick, OrchidSelection } from "../../model/types";
import BedZoneBlock from "./BedZoneBlock";

export default function PhysicalBedBlock({
  bed,
  distinguishVarietyColors,
  filteredOrchidGroupIds,
  selection,
  showScale,
  cellRangePick,
  onPickCellRange,
  onSelectBedZone,
  onSelectPhysicalBed,
  onSelectOrchidGroup,
}: {
  bed: PhysicalBed;
  distinguishVarietyColors: boolean;
  filteredOrchidGroupIds: Set<number>;
  selection: OrchidSelection | null;
  showScale: boolean;
  cellRangePick: MapCellRangePick;
  onPickCellRange: (bedZoneId: number, cell: number) => void;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectPhysicalBed: (physicalBedId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  const selected =
    selection?.type === "PHYSICAL_BED" && selection.physicalBedId === bed.id;

  return (
    <div
      className={`cursor-pointer rounded-md border p-2 transition ${
        selected
          ? "border-[#246df2] bg-[#f4f8ff] ring-2 ring-[#246df2]/20"
          : "border-[#cfe0cc] bg-[#f7faf6] hover:border-[#159447]"
      }`}
      role="button"
      tabIndex={0}
      onClick={(event) => {
        event.stopPropagation();
        onSelectPhysicalBed(bed.id);
      }}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onSelectPhysicalBed(bed.id);
        }
      }}
    >
      <p className="px-2 text-center text-sm font-semibold">{bed.number}다이</p>
      <div className="mt-1 grid grid-cols-2 gap-2">
        {bed.bedZones.map((zone) => (
          <BedZoneBlock
            key={zone.id}
            distinguishVarietyColors={distinguishVarietyColors}
            filteredOrchidGroupIds={filteredOrchidGroupIds}
            maxPosition={bed.positionUnitCount}
            showScale={showScale}
            cellRangePick={cellRangePick}
            zone={zone}
            selected={
              selection?.type === "BED_ZONE" && selection.bedZoneId === zone.id
            }
            selectedOrchidGroupId={
              selection?.type === "ORCHID_GROUP"
                ? selection.orchidGroupId
                : null
            }
            onPickCellRange={onPickCellRange}
            onSelectBedZone={onSelectBedZone}
            onSelectOrchidGroup={onSelectOrchidGroup}
          />
        ))}
      </div>
    </div>
  );
}
