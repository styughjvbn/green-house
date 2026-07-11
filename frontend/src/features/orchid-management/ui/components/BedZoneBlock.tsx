"use client";

import type { DragEvent, MouseEvent, PointerEvent } from "react";
import type { BedZone } from "@/entities/farm/types";
import type { DragState, MapCellRangePick } from "../../model/types";
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
  cellRangePick,
  zone,
  selected,
  selectedOrchidGroupId,
  onDragEnd,
  onDragStart,
  onDropOnBedZone,
  onEnterDropZone,
  onPickCellRange,
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
  cellRangePick: MapCellRangePick;
  zone: BedZone;
  selected: boolean;
  selectedOrchidGroupId: number | null;
  onDragEnd: () => void;
  onDragStart: (orchidGroupId: number) => void;
  onDropOnBedZone: (bedZoneId: number) => Promise<void>;
  onEnterDropZone: (bedZoneId: number) => void;
  onPickCellRange: (bedZoneId: number, cell: number) => void;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  const dropActive = dragState?.overBedZoneId === zone.id;
  const resolvedMaxPosition = maxPosition && maxPosition > 0 ? maxPosition : 28;
  const scaleMarks = buildScaleMarks(resolvedMaxPosition);
  const guideLines = buildGuideLines(resolvedMaxPosition);
  const cellHeight = MAP_HEIGHT / resolvedMaxPosition;
  const rangePickActive =
    cellRangePick.active && cellRangePick.targetBedZoneId === zone.id;
  const pickedStartCell =
    cellRangePick.targetBedZoneId === zone.id ? cellRangePick.startCell : null;
  const pickedEndCell =
    cellRangePick.targetBedZoneId === zone.id ? cellRangePick.endCell : null;
  const pickedRange =
    pickedStartCell != null && pickedEndCell != null
      ? {
          start: Math.min(pickedStartCell, pickedEndCell),
          end: Math.max(pickedStartCell, pickedEndCell),
        }
      : null;

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
    if (event.pointerType === "mouse" || dragState || cellRangePick.active) {
      return;
    }
    onSelectBedZone(zone.id);
  }

  function handlePickCell(event: MouseEvent<HTMLDivElement>) {
    if (!rangePickActive) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    const rect = event.currentTarget.getBoundingClientRect();
    const y = Math.min(Math.max(event.clientY - rect.top, 0), rect.height - 1);
    const cellFromTop = Math.floor(y / (rect.height / resolvedMaxPosition));
    const cell = Math.min(
      Math.max(resolvedMaxPosition - cellFromTop, 1),
      resolvedMaxPosition,
    );
    onPickCellRange(zone.id, cell);
  }

  return (
    <div
      className={`touch-manipulation rounded-l border p-2 text-left transition ${
        selected
          ? "border-[#246df2] bg-[#f4f8ff] ring-2 ring-[#246df2]/20"
          : "border-[#d9e1d8] bg-white hover:border-[#159447]"
      } ${dropActive || rangePickActive ? "border-[#159447] bg-[#eef7ec] ring-2 ring-[#159447]/20" : ""}`}
      onClick={() => {
        if (!cellRangePick.active) {
          onSelectBedZone(zone.id);
        }
      }}
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
          className={`relative min-w-0 flex-1 overflow-hidden border border-[#e4e8e4] bg-white ${
            rangePickActive ? "cursor-crosshair" : ""
          }`}
          style={{ height: MAP_HEIGHT }}
          onClick={handlePickCell}
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

          {pickedRange ? (
            <div
              className="pointer-events-none absolute inset-x-0 z-10 border-y border-[#159447] bg-[#159447]/20"
              style={{
                top: `${toPercent(resolvedMaxPosition - pickedRange.end, resolvedMaxPosition)}%`,
                height: `${toPercent(pickedRange.end - pickedRange.start + 1, resolvedMaxPosition)}%`,
              }}
            />
          ) : null}

          {rangePickActive && !pickedRange ? (
            <div className="pointer-events-none absolute inset-x-2 top-2 z-20 rounded-md border border-dashed border-[#159447] bg-white/90 px-2 py-1 text-center text-xs font-bold text-[#167c3a]">
              시작 칸을 선택하세요
            </div>
          ) : null}

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
