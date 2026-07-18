"use client";

import type { House } from "@/entities/farm/types";
import type { MapCellRangePick, OrchidSelection } from "../../model/types";
import PhysicalBedBlock from "./PhysicalBedBlock";

export default function HouseDetailMap({
  distinguishVarietyColors,
  filteredOrchidGroupIds,
  house,
  selectedOrchidGroupIds,
  selection,
  showScale,
  cellRangePick,
  onPickCellRange,
  onSelectBedZone,
  onSelectHouse,
  onSelectPhysicalBed,
  onSelectOrchidGroup,
}: {
  distinguishVarietyColors: boolean;
  filteredOrchidGroupIds: Set<number>;
  house: House;
  selectedOrchidGroupIds: Set<number>;
  selection: OrchidSelection | null;
  showScale: boolean;
  cellRangePick: MapCellRangePick;
  onPickCellRange: (bedZoneId: number, cell: number) => void;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectHouse: () => void;
  onSelectPhysicalBed: (physicalBedId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  return (
    <section
      className="h-full min-h-0 cursor-pointer overflow-hidden rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm"
      onClick={onSelectHouse}
    >
      <div className="grid h-full min-h-0 gap-3 lg:grid-cols-3">
        {house.physicalBeds.map((bed) => (
          <PhysicalBedBlock
            key={bed.id}
            bed={bed}
            distinguishVarietyColors={distinguishVarietyColors}
            filteredOrchidGroupIds={filteredOrchidGroupIds}
            selectedOrchidGroupIds={selectedOrchidGroupIds}
            selection={selection}
            showScale={showScale}
            cellRangePick={cellRangePick}
            onPickCellRange={onPickCellRange}
            onSelectBedZone={onSelectBedZone}
            onSelectPhysicalBed={onSelectPhysicalBed}
            onSelectOrchidGroup={onSelectOrchidGroup}
          />
        ))}
      </div>
    </section>
  );
}
