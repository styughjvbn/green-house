"use client";

import type { DragEvent, PointerEvent } from "react";
import type { BedZone } from "@/entities/farm/types";
import type { DragState, MapCellRangePick } from "../../model/types";
import {
  buildOccupiedCells,
  clampCellRangeToAvailable,
  formatCellRange,
  resolveGroupCellRange,
} from "../../lib/orchidManagementUtils";
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
  const cellHeight = MAP_HEIGHT / resolvedMaxPosition;
  const cells = buildCells(resolvedMaxPosition);
  const rangePickActive =
    cellRangePick.active &&
    (cellRangePick.targetBedZoneId == null ||
      cellRangePick.targetBedZoneId === zone.id);
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
  const occupiedCells = buildOccupiedCells(
    zone.orchidGroups,
    cellRangePick.excludeOrchidGroupId,
    resolvedMaxPosition,
  );

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
          <div
            className="grid w-5 shrink-0"
            style={{
              height: MAP_HEIGHT,
              gridTemplateRows: `repeat(${resolvedMaxPosition}, minmax(0, 1fr))`,
            }}
          >
            {cells.map((cell) => (
              <div
                key={cell}
                className="flex min-h-0 items-start justify-end gap-0.5"
              >
                <span className="text-[11px] font-bold text-[#2d5a3b]">
                  {isScaleLabelCell(cell, resolvedMaxPosition) ? cell : ""}
                </span>
              </div>
            ))}
          </div>
        ) : null}

        <div
          className={`relative grid min-w-0 flex-1 overflow-hidden border border-[#e4e8e4] bg-white ${
            rangePickActive ? "cursor-crosshair" : ""
          }`}
          style={{
            height: MAP_HEIGHT,
            gridTemplateRows: `repeat(${resolvedMaxPosition}, minmax(0, 1fr))`,
          }}
        >
          {cells.map((cell) => {
            const selectedCell =
              pickedRange != null &&
              cell >= pickedRange.start &&
              cell <= pickedRange.end;
            const occupiedCell = occupiedCells.has(cell);
            const boundary = resolvedMaxPosition - cell + 1;
            const major = isScaleLabelCell(cell, resolvedMaxPosition);
            const cellClass = `z-0 border-t ${
              major ? "border-[#d9e2d7]" : "border-[#edf1ec]"
            } ${
              selectedCell
                ? "bg-[#159447]/20"
                : occupiedCell
                  ? "bg-[#eef1ed]"
                  : "bg-transparent"
            }`;
            const cellStyle = {
              gridColumn: "1",
              gridRow: `${boundary} / ${boundary + 1}`,
            };

            return (
              <div
                key={cell}
                className={`${cellClass} appearance-none p-0 transition ${
                  rangePickActive ? "hover:bg-[#159447]/15" : ""
                }`}
                style={cellStyle}
                onClick={(event) => {
                  if (!rangePickActive) {
                    return;
                  }
                  event.preventDefault();
                  event.stopPropagation();
                  const selectingStart =
                    pickedStartCell == null ||
                    (pickedEndCell != null &&
                      pickedStartCell !== pickedEndCell);
                  const nextRange = clampCellRangeToAvailable({
                    startCell: selectingStart ? cell : pickedStartCell,
                    endCell: cell,
                    occupiedCells,
                  });
                  if (!nextRange) {
                    return;
                  }
                  const nextCell =
                    !selectingStart && cell < pickedStartCell
                      ? nextRange.startCell
                      : nextRange.endCell;
                  onPickCellRange(zone.id, nextCell);
                }}
              />
            );
          })}

          {rangePickActive && !pickedRange ? (
            <div className="pointer-events-none absolute inset-x-2 top-2 z-20 rounded-md border border-dashed border-[#159447] bg-white/90 px-2 py-1 text-center text-xs font-bold text-[#167c3a]">
              시작 칸을 선택하세요
            </div>
          ) : null}

          {zone.orchidGroups.map((orchidGroup) => {
            const matched = filteredOrchidGroupIds.has(orchidGroup.id);
            const range = resolveGroupCellRange({
              startPosition: orchidGroup.startPosition,
              endPosition: orchidGroup.endPosition,
              maxCell: resolvedMaxPosition,
            });
            const gridRowStart = resolvedMaxPosition - range.endCell + 1;
            const gridRowEnd = resolvedMaxPosition - range.startCell + 2;
            const heightPx = (range.endCell - range.startCell + 1) * cellHeight;

            return (
              <div
                key={orchidGroup.id}
                className={`z-10 min-h-0 ${
                  rangePickActive
                    ? "pointer-events-none"
                    : "pointer-events-auto"
                }`}
                style={{
                  gridRow: `${gridRowStart} / ${gridRowEnd}`,
                  gridColumn: "1",
                }}
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

function buildCells(maxPosition: number) {
  return Array.from({ length: maxPosition }, (_, index) => maxPosition - index);
}

function isScaleLabelCell(cell: number, maxCell: number) {
  return cell === maxCell || cell % 5 === 0;
}
