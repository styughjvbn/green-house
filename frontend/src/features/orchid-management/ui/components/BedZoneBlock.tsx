"use client";

import type { PointerEvent } from "react";
import type { BedZone } from "@/entities/farm/types";
import type { MapCellRangePick } from "../../model/types";
import {
  buildOccupiedCells,
  formatCellRange,
  resolveGroupCellRange,
} from "../../lib/orchidManagementUtils";
import OrchidGroupBlock from "./OrchidGroupBlock";

const DENSITY_REFERENCE_HEIGHT = 590;

export default function BedZoneBlock({
  maxPosition,
  distinguishVarietyColors,
  filteredOrchidGroupIds,
  showScale,
  cellRangePick,
  zone,
  selected,
  selectedOrchidGroupId,
  selectedOrchidGroupIds,
  onPickCellRange,
  onSelectBedZone,
  onSelectOrchidGroup,
}: {
  maxPosition: number | null;
  distinguishVarietyColors: boolean;
  filteredOrchidGroupIds: Set<number>;
  showScale: boolean;
  cellRangePick: MapCellRangePick;
  zone: BedZone;
  selected: boolean;
  selectedOrchidGroupId: number | null;
  selectedOrchidGroupIds: Set<number>;
  onPickCellRange: (bedZoneId: number, cell: number) => void;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  const resolvedMaxPosition = maxPosition && maxPosition > 0 ? maxPosition : 28;
  const densityCellHeight = DENSITY_REFERENCE_HEIGHT / resolvedMaxPosition;
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

  function handlePointerUp(event: PointerEvent<HTMLDivElement>) {
    if (event.pointerType === "mouse" || cellRangePick.active) {
      return;
    }
    onSelectBedZone(zone.id);
  }

  return (
    <div
      className={`flex h-full min-h-0 touch-manipulation flex-col rounded-l border p-2 text-left transition ${
        selected
          ? "border-[#246df2] bg-[#f4f8ff] ring-2 ring-[#246df2]/20"
          : "border-[#d9e1d8] bg-white hover:border-[#159447]"
      } ${rangePickActive ? "border-[#159447] bg-[#eef7ec] ring-2 ring-[#159447]/20" : ""}`}
      onClick={(event) => {
        event.stopPropagation();
        if (!cellRangePick.active) {
          onSelectBedZone(zone.id);
        }
      }}
      onPointerUp={handlePointerUp}
      role="button"
      tabIndex={0}
    >
      <div className="flex min-h-0 flex-1 gap-0">
        {showScale ? (
          <div
            className="grid h-full w-3 shrink-0"
            style={{
              gridTemplateRows: `repeat(${resolvedMaxPosition}, minmax(0, 1fr))`,
            }}
          >
            {cells.map((cell) => (
              <div
                key={cell}
                className="flex min-h-0 items-start justify-end gap-0.5"
              >
                {isScaleLabelCell(cell, resolvedMaxPosition) ? (
                  <div className="flex h-full -translate-x-2 items-center">
                    <span className="text-[11px] font-bold text-[#2d5a3b]">
                      {cell}
                    </span>
                    <div className="relative h-full w-1 shrink-0">
                      <span className="absolute top-0 left-0 h-full w-[1px] bg-[#d9e2d7]" />
                      <span className="absolute top-0 left-0 h-[1px] w-full bg-[#d9e2d7]" />
                      <span className="absolute bottom-0 left-0 h-[1px] w-full bg-[#d9e2d7]" />
                    </div>
                  </div>
                ) : (
                  ""
                )}
              </div>
            ))}
          </div>
        ) : null}

        <div
          className={`relative grid h-full min-w-0 flex-1 overflow-hidden border border-[#e4e8e4] bg-white ${
            rangePickActive ? "cursor-crosshair" : ""
          }`}
          style={{
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
                  if (selectingStart && occupiedCells.has(cell)) {
                    return;
                  }
                  onPickCellRange(zone.id, cell);
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
            const previewRange =
              cellRangePick.excludeOrchidGroupId === orchidGroup.id &&
              cellRangePick.targetBedZoneId === zone.id &&
              cellRangePick.startCell != null &&
              cellRangePick.endCell != null
                ? {
                    startCell: Math.min(
                      cellRangePick.startCell,
                      cellRangePick.endCell,
                    ),
                    endCell: Math.max(
                      cellRangePick.startCell,
                      cellRangePick.endCell,
                    ),
                  }
                : null;
            const previewMoved =
              previewRange != null &&
              (previewRange.startCell !== range.startCell ||
                previewRange.endCell !== range.endCell);
            const displayRange = previewMoved ? previewRange : range;
            const rangePickExcluded =
              cellRangePick.excludeOrchidGroupId === orchidGroup.id &&
              (rangePickActive || previewMoved);
            const gridRowStart = resolvedMaxPosition - displayRange.endCell + 1;
            const gridRowEnd = resolvedMaxPosition - displayRange.startCell + 2;
            const heightPx =
              (displayRange.endCell - displayRange.startCell + 1) *
              densityCellHeight;

            return (
              <div
                key={orchidGroup.id}
                className={`z-10 min-h-0 min-w-0 ${
                  rangePickActive
                    ? "pointer-events-none"
                    : "pointer-events-auto"
                } ${rangePickExcluded ? "opacity-30" : ""} ${
                  rangePickExcluded ? "transition-opacity" : ""
                }`}
                style={{
                  gridRow: `${gridRowStart} / ${gridRowEnd}`,
                  gridColumn: "1",
                }}
              >
                <OrchidGroupBlock
                  distinguishVarietyColors={distinguishVarietyColors}
                  heightPx={heightPx}
                  muted={!matched}
                  orchidGroup={orchidGroup}
                  positionLabel={
                    previewMoved
                      ? `${displayRange.startCell}-${displayRange.endCell}칸`
                      : formatCellRange(orchidGroup)
                  }
                  selected={
                    selectedOrchidGroupId === orchidGroup.id ||
                    selectedOrchidGroupIds.has(orchidGroup.id)
                  }
                  onSelect={() => onSelectOrchidGroup(orchidGroup.id)}
                />
              </div>
            );
          })}
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
