"use client";

import type { PhysicalBed } from "@/entities/farm/types";
import type { DragState, OrchidSelection } from "../../model/types";
import BedZoneBlock from "./BedZoneBlock";

export default function PhysicalBedBlock({
  bed,
  dragState,
  filteredOrchidGroupIds,
  placementEditMode,
  saving,
  selection,
  showScale,
  onDragEnd,
  onDragStart,
  onDropOnBedZone,
  onEnterDropZone,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  bed: PhysicalBed;
  dragState: DragState;
  filteredOrchidGroupIds: Set<number>;
  placementEditMode: boolean;
  saving: boolean;
  selection: OrchidSelection | null;
  showScale: boolean;
  onDragEnd: () => void;
  onDragStart: (orchidGroupId: number) => void;
  onDropOnBedZone: (bedZoneId: number) => Promise<void>;
  onEnterDropZone: (bedZoneId: number) => void;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  return (
    <div className="rounded-md border border-[#cfe0cc] bg-[#f7faf6] p-2">
      <div className="mt-2 grid grid-cols-2 gap-2">
        {bed.bedZones.map((zone) => (
          <BedZoneBlock
            key={zone.id}
            dragState={dragState}
            filteredOrchidGroupIds={filteredOrchidGroupIds}
            maxPosition={bed.positionUnitCount}
            placementEditMode={placementEditMode}
            saving={saving}
            showScale={showScale}
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
            onSelectBedZone={onSelectBedZone}
            onSelectOrchidGroup={onSelectOrchidGroup}
          />
        ))}
      </div>
    </div>
  );
}
