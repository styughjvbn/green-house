"use client";

import type { BedZone } from "@/types/farm";
import OrchidGroupBlock from "./OrchidGroupBlock";

export default function BedZoneBlock({
  zone,
  selected,
  selectedOrchidGroupId,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  zone: BedZone;
  selected: boolean;
  selectedOrchidGroupId: number | null;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  const emptySlotCount = Math.max(1, 5 - zone.orchidGroups.length);

  return (
    <div
      className={`min-h-[360px] rounded-md border p-2 text-left transition ${
        selected ? "border-[#246df2] bg-[#f3f7ff] ring-2 ring-[#246df2]" : "border-[#d7ddd4] bg-white hover:border-[#159447]"
      }`}
      onClick={() => onSelectBedZone(zone.id)}
      role="button"
      tabIndex={0}
    >
      <p className="text-center text-base font-semibold">{zone.side === "LEFT" ? "좌" : "우"}</p>
      <div className="mt-2 space-y-2">
        {zone.orchidGroups.map((orchidGroup) => (
          <OrchidGroupBlock
            key={orchidGroup.id}
            orchidGroup={orchidGroup}
            selected={selectedOrchidGroupId === orchidGroup.id}
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
