"use client";

import type { DragEvent, PointerEvent } from "react";
import type { BedZone } from "@/entities/farm/types";
import type { DragState } from "../../model/types";
import { formatCellRange } from "../../lib/orchidManagementUtils";
import OrchidGroupBlock from "./OrchidGroupBlock";

const MAP_HEIGHT = 590;

export default function BedZoneBlock({
  maxPosition,
  distinguishVarietyColors,
  dragState,
  filteredOrchidGroupIds,
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
  distinguishVarietyColors: boolean;
  dragState: DragState;
  filteredOrchidGroupIds: Set<number>;
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
  const scaleMarks = buildScaleMarks(resolvedMaxPosition);
  const guideLines = buildGuideLines(resolvedMaxPosition);
  const cellHeight = MAP_HEIGHT / resolvedMaxPosition;

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
          <div className="relative w-5 shrink-0" style={{ height: MAP_HEIGHT }}>
            {scaleMarks.map((mark) => (
              <div
                key={mark.label}
                className="absolute right-1 flex w-8 items-center justify-end gap-0.5"
                style={{ top: `${mark.top}%` }}
              >
                <span className="text-[11px] font-bold text-[#2d5a3b]">
                  {mark.label}
                </span>
                <div
                  className="relative w-1 shrink-0"
                  style={{ height: cellHeight }}
                >
                  <span className="absolute top-0 left-0 h-full w-[2px] bg-[#d9e2d7]" />
                  <span className="absolute top-0 left-0 h-[2px] w-full bg-[#d9e2d7]" />
                  <span className="absolute bottom-0 left-0 h-[2px] w-full bg-[#d9e2d7]" />
                </div>
              </div>
            ))}
          </div>
        ) : null}

        <div
          className="relative min-w-0 flex-1 overflow-hidden border border-[#e4e8e4] bg-white"
          style={{ height: MAP_HEIGHT }}
        >
          {showScale
            ? guideLines.map((line) => (
                <div
                  key={`guide-${line.boundary}`}
                  className={`absolute inset-x-0 border-t ${
                    line.major ? "border-[#d9e2d7]" : "border-[#edf1ec]"
                  }`}
                  style={{ top: `${line.top}%` }}
                />
              ))
            : null}

          {zone.orchidGroups.map((orchidGroup) => {
            const matched = filteredOrchidGroupIds.has(orchidGroup.id);
            const start = orchidGroup.startPosition ?? 0;
            const end =
              orchidGroup.endPosition ?? orchidGroup.startPosition ?? 0;
            const top = toPercent(
              resolvedMaxPosition - end,
              resolvedMaxPosition,
            );
            const height = Math.max(
              toPercent(end - start, resolvedMaxPosition),
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
                  distinguishVarietyColors={distinguishVarietyColors}
                  draggable={matched && placementEditMode && !saving}
                  heightPx={heightPx}
                  muted={!matched}
                  orchidGroup={orchidGroup}
                  positionLabel={formatCellRange(orchidGroup)}
                  selected={selectedOrchidGroupId === orchidGroup.id}
                  onDragEnd={onDragEnd}
                  onDragStart={() => onDragStart(orchidGroup.id)}
                  onSelect={() => onSelectOrchidGroup(orchidGroup.id)}
                />
              </div>
            );
          })}

          {!zone.orchidGroups.length ? (
            <div className="absolute inset-x-2 top-2 rounded-lg border border-dashed border-[#d8ded8] bg-[#f6f7f5] px-3 py-3 text-center text-xs font-medium text-[#8a948c]">
              빈 구역
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function toPercent(value: number, maxPosition: number) {
  if (maxPosition === 0) {
    return 0;
  }
  return (value / maxPosition) * 100;
}

function buildScaleMarks(maxPosition: number) {
  const labels = new Set<number>([maxPosition]);
  for (let value = 5; value < maxPosition; value += 5) {
    labels.add(value);
  }

  return [...labels]
    .sort((a, b) => b - a)
    .map((label) => ({
      label,
      top: toPercent(maxPosition - label, maxPosition),
    }));
}

function buildGuideLines(maxPosition: number) {
  return Array.from({ length: maxPosition - 1 }, (_, index) => {
    const boundary = index + 1;
    const label = maxPosition - boundary;

    return {
      boundary,
      major: label > 0 && label % 5 === 0,
      top: toPercent(boundary, maxPosition),
    };
  });
}
