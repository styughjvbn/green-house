"use client";

import type { House } from "@/entities/farm/types";
import type { MapCellRangePick, OrchidSelection } from "../../model/types";
import PhysicalBedBlock from "./PhysicalBedBlock";

export default function HouseDetailMap({
  distinguishVarietyColors,
  filteredOrchidGroupIds,
  house,
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
      className="cursor-pointer rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm"
      onClick={onSelectHouse}
    >
      <div className="grid gap-3 xl:grid-cols-3">
        {house.physicalBeds.map((bed) => (
          <PhysicalBedBlock
            key={bed.id}
            bed={bed}
            distinguishVarietyColors={distinguishVarietyColors}
            filteredOrchidGroupIds={filteredOrchidGroupIds}
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
