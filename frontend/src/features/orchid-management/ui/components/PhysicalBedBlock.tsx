"use client";

import type { PhysicalBed } from "@/entities/farm/types";
import type {
  DragState,
  MapCellRangePick,
  OrchidSelection,
} from "../../model/types";
import BedZoneBlock from "./BedZoneBlock";

export default function PhysicalBedBlock({
  bed,
  distinguishVarietyColors,
  dragState,
  filteredOrchidGroupIds,
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
  onSelectOrchidGroup,
}: {
  bed: PhysicalBed;
  distinguishVarietyColors: boolean;
  dragState: DragState;
  filteredOrchidGroupIds: Set<number>;
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
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  return (
    <div className="rounded-md border border-[#cfe0cc] bg-[#f7faf6] p-2">
      <div className="mt-2 grid grid-cols-2 gap-2">
        {bed.bedZones.map((zone) => (
          <BedZoneBlock
            key={zone.id}
            distinguishVarietyColors={distinguishVarietyColors}
            dragState={dragState}
            filteredOrchidGroupIds={filteredOrchidGroupIds}
            maxPosition={bed.positionUnitCount}
            placementEditMode={placementEditMode}
            saving={saving}
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
            onDragEnd={onDragEnd}
            onDragStart={onDragStart}
            onDropOnBedZone={onDropOnBedZone}
            onEnterDropZone={onEnterDropZone}
            onPickCellRange={onPickCellRange}
            onSelectBedZone={onSelectBedZone}
            onSelectOrchidGroup={onSelectOrchidGroup}
          />
        ))}
      </div>
    </div>
  );
}
