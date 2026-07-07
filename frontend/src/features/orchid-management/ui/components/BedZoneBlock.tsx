"use client";

import type { DragEvent, PointerEvent } from "react";
import type { BedZone } from "@/entities/farm/types";
import type { DragState } from "../../model/types";
import OrchidGroupBlock from "./OrchidGroupBlock";

const MAP_HEIGHT = 420;

export default function BedZoneBlock({
  maxPosition,
  dragState,
  placementEditMode,
  saving,
  showScale,
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
  maxPosition: number | null;
  dragState: DragState;
  placementEditMode: boolean;
  saving: boolean;
  showScale: boolean;
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
  const dropActive = dragState?.overBedZoneId === zone.id;
  const resolvedMaxPosition = maxPosition && maxPosition > 0 ? maxPosition : 28;
  const marks = buildMarks(resolvedMaxPosition);

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

  function handlePointerUp(event: PointerEvent<HTMLDivElement>) {
    if (event.pointerType === "mouse" || dragState) {
      return;
    }
    onSelectBedZone(zone.id);
  }

  return (
    <div
      className={`touch-manipulation rounded-l border p-2 text-left transition ${
        selected
          ? "border-[#246df2] bg-[#f4f8ff] ring-2 ring-[#246df2]/20"
          : "border-[#d9e1d8] bg-white hover:border-[#159447]"
      } ${dropActive ? "border-[#159447] bg-[#eef7ec] ring-2 ring-[#159447]/20" : ""}`}
      onClick={() => onSelectBedZone(zone.id)}
      onDragOver={handleDragOver}
      onDrop={(event) => void handleDrop(event)}
      onPointerUp={handlePointerUp}
      role="button"
      tabIndex={0}
    >
      <div className="flex gap-0">
        {showScale ? (
          <div
            className="relative w-1 shrink-0 py-2"
            style={{ height: MAP_HEIGHT - 4 }}
          >
            {marks.map((mark) => (
              <div
                key={mark.value}
                className="absolute inset-x-0"
                style={{ top: `${mark.top}%` }}
              >
                <span className="absolute left-0 -translate-x-full -translate-y-1/2 text-[10px] font-semibold text-[#6f7b72]">
                  {mark.value}
                </span>
              </div>
            ))}
          </div>
        ) : null}

        <div
          className="relative min-w-0 flex-1 overflow-hidden border border-[#e4e8e4] bg-white"
          style={{ height: MAP_HEIGHT }}
        >
          {showScale
            ? marks
                .slice(1, -1)
                .map((mark) => (
                  <div
                    key={`guide-${mark.value}`}
                    className="absolute inset-x-0 border-t border-dashed border-[#e5e7e4]"
                    style={{ top: `${mark.top}%` }}
                  />
                ))
            : null}

          {zone.orchidGroups.map((orchidGroup) => {
            const start = orchidGroup.startPosition ?? 0;
            const end =
              orchidGroup.endPosition ?? orchidGroup.startPosition ?? 0;
            const top = toPercent(start, resolvedMaxPosition);
            const height = Math.max(
              toPercent(end, resolvedMaxPosition) - top,
              0,
            );
            const heightPx = (height / 100) * MAP_HEIGHT;

            return (
              <div
                key={orchidGroup.id}
                className="absolute inset-x-0"
                style={{ top: `${top}%`, height: `${height}%` }}
              >
                <OrchidGroupBlock
                  draggable={placementEditMode && !saving}
                  heightPx={heightPx}
                  orchidGroup={orchidGroup}
                  selected={selectedOrchidGroupId === orchidGroup.id}
                  onDragEnd={onDragEnd}
                  onDragStart={() => onDragStart(orchidGroup.id)}
                  onSelect={() => onSelectOrchidGroup(orchidGroup.id)}
                />
              </div>
            );
          })}

          {!zone.orchidGroups.length ? (
            <div className="absolute inset-x-2 top-3 rounded-lg border border-dashed border-[#d8ded8] bg-[#f6f7f5] px-3 py-3 text-center text-xs font-medium text-[#8a948c]">
              빈 구역
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function buildMarks(maxPosition: number) {
  const step = maxPosition / 4;
  return [0, 1, 2, 3, 4].map((index) => {
    const value = Math.round(step * index);
    return {
      value,
      top: maxPosition === 0 ? 0 : (value / maxPosition) * 100,
    };
  });
}

function toPercent(value: number, maxPosition: number) {
  if (maxPosition === 0) {
    return 0;
  }
  return (value / maxPosition) * 100;
}
