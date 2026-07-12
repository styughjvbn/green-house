"use client";

import type { House } from "@/entities/farm/types";
import type {
  DragState,
  MapCellRangePick,
  OrchidSelection,
} from "../../model/types";
import PhysicalBedBlock from "./PhysicalBedBlock";

export default function HouseDetailMap({
  distinguishVarietyColors,
  dragState,
  filteredOrchidGroupIds,
  house,
  placementEditMode,
  saving,
  selection,
  showScale,
  cellRangePick,
  onDragEnd,
  onDragStart,
  onDropOnBedZone,
  onEnterDropZone,
  onPickCellRange,
  onSelectBedZone,
  onSelectPhysicalBed,
  onSelectOrchidGroup,
}: {
  distinguishVarietyColors: boolean;
  dragState: DragState;
  filteredOrchidGroupIds: Set<number>;
  house: House;
  placementEditMode: boolean;
  saving: boolean;
  selection: OrchidSelection | null;
  showScale: boolean;
  cellRangePick: MapCellRangePick;
  onDragEnd: () => void;
  onDragStart: (orchidGroupId: number) => void;
  onDropOnBedZone: (bedZoneId: number) => Promise<void>;
  onEnterDropZone: (bedZoneId: number) => void;
  onPickCellRange: (bedZoneId: number, cell: number) => void;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectPhysicalBed: (physicalBedId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
      <div className="grid gap-3 xl:grid-cols-3">
        {house.physicalBeds.map((bed) => (
          <PhysicalBedBlock
            key={bed.id}
            bed={bed}
            distinguishVarietyColors={distinguishVarietyColors}
            dragState={dragState}
            filteredOrchidGroupIds={filteredOrchidGroupIds}
            placementEditMode={placementEditMode}
            saving={saving}
            selection={selection}
            showScale={showScale}
            cellRangePick={cellRangePick}
            onDragEnd={onDragEnd}
            onDragStart={onDragStart}
            onDropOnBedZone={onDropOnBedZone}
            onEnterDropZone={onEnterDropZone}
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
