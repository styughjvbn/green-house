"use client";

import type { DragEvent } from "react";
import type { BedZone } from "@/entities/farm/types";
import type { DragState } from "../../model/types";
import OrchidGroupBlock from "./OrchidGroupBlock";

export default function BedZoneBlock({
  dragState,
  placementEditMode,
  saving,
  zone,
  selected,
  selectedOrchidGroupId,
  onDragEnd,
  onDragStart,
  onDropOnBedZone,
  onEnterDropZone,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  dragState: DragState;
  placementEditMode: boolean;
  saving: boolean;
  zone: BedZone;
  selected: boolean;
  selectedOrchidGroupId: number | null;
  onDragEnd: () => void;
  onDragStart: (orchidGroupId: number) => void;
  onDropOnBedZone: (bedZoneId: number) => Promise<void>;
  onEnterDropZone: (bedZoneId: number) => void;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  const emptySlotCount = Math.max(1, 5 - zone.orchidGroups.length);
  const dropActive = dragState?.overBedZoneId === zone.id;

  function handleDragOver(event: DragEvent<HTMLDivElement>) {
    if (!placementEditMode || !dragState || saving) {
      return;
    }
    event.preventDefault();
    onEnterDropZone(zone.id);
  }

  async function handleDrop(event: DragEvent<HTMLDivElement>) {
    if (!placementEditMode || !dragState || saving) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    await onDropOnBedZone(zone.id);
  }

  return (
    <div
      className={`min-h-[360px] rounded-md border p-2 text-left transition ${
        selected ? "border-[#246df2] bg-[#f3f7ff] ring-2 ring-[#246df2]" : "border-[#d7ddd4] bg-white hover:border-[#159447]"
      } ${dropActive ? "border-[#159447] bg-[#eef7ec] ring-2 ring-[#159447]" : ""}`}
      onClick={() => onSelectBedZone(zone.id)}
      onDragOver={handleDragOver}
      onDrop={(event) => void handleDrop(event)}
      role="button"
      tabIndex={0}
    >
      <p className="text-center text-base font-semibold">{zone.side === "LEFT" ? "좌" : "우"}</p>
      <div className="mt-2 space-y-2">
        {zone.orchidGroups.map((orchidGroup) => (
          <OrchidGroupBlock
            key={orchidGroup.id}
            draggable={placementEditMode && !saving}
            orchidGroup={orchidGroup}
            selected={selectedOrchidGroupId === orchidGroup.id}
            onDragEnd={onDragEnd}
            onDragStart={() => onDragStart(orchidGroup.id)}
            onSelect={(event) => {
              event.stopPropagation();
              onSelectOrchidGroup(orchidGroup.id);
            }}
          />
        ))}
        {Array.from({ length: emptySlotCount }, (_, index) => (
          <div key={index} className="min-h-12 rounded-md border border-dashed border-[#d7ddd4] bg-[#f0f1ef]" />
        ))}
      </div>
    </div>
  );
}
