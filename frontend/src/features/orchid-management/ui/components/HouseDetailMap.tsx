"use client";

import type { House } from "@/entities/farm/types";
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
  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
      <div className="grid gap-3 xl:grid-cols-3">
        {house.physicalBeds.map((bed) => (
          <PhysicalBedBlock
            key={bed.id}
            bed={bed}
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
