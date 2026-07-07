"use client";

import type { DragEvent, PointerEvent } from "react";
import type {
  BedZone,
  BedZonePlacementProfile,
  BedZoneSegment,
  OrchidGroup,
} from "@/entities/farm/types";
import type { DragState } from "../../model/types";
import OrchidGroupBlock from "./OrchidGroupBlock";

const MAP_HEIGHT = 420;
const FALLBACK_SEGMENT_HEIGHT = 72;

export default function BedZoneBlock({
  maxPosition,
  dragState,
  placementEditMode,
  profile,
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
  maxPosition: number | null;
  dragState: DragState;
  placementEditMode: boolean;
  profile: BedZonePlacementProfile | null;
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
  const dropActive = dragState?.overBedZoneId === zone.id;
  const resolvedMaxPosition = resolveMaxPosition(
    profile?.positionUnitCount ?? maxPosition,
  );
  const marks = buildMarks(resolvedMaxPosition);
  const segments = resolveSegments(profile, resolvedMaxPosition, zone.side);

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
      className={`touch-manipulation rounded-xl border p-2 text-left transition ${
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
      <div className="mb-2">
        <p className="text-center text-2xl font-bold text-[#243126]">
          {zone.side === "LEFT" ? "좌" : "우"}
        </p>
      </div>

      <div className="flex gap-2">
        <div className="relative w-8 shrink-0" style={{ height: MAP_HEIGHT }}>
          <div className="absolute top-2 bottom-0 left-5 w-px border-l border-dashed border-[#cfd7d0]" />
          {marks.map((mark) => (
            <div
              key={mark.value}
              className="absolute inset-x-0"
              style={{ top: `${mark.top}%` }}
            >
              <span className="absolute left-0 -translate-x-full -translate-y-1/2 text-xs font-semibold text-[#6f7b72]">
                {mark.value}
              </span>
              <span className="absolute top-1/2 left-4 h-px w-2 -translate-y-1/2 bg-[#bfc8c0]" />
            </div>
          ))}
        </div>

        <div
          className="relative min-w-0 flex-1 overflow-hidden rounded-xl border border-[#e4e8e4] bg-white"
          style={{ height: MAP_HEIGHT }}
        >
          {marks.slice(1, -1).map((mark) => (
            <div
              key={`guide-${mark.value}`}
              className="absolute inset-x-0 border-t border-dashed border-[#e5e7e4]"
              style={{ top: `${mark.top}%` }}
            />
          ))}

          {segments.map((segment, index) => {
            const top = positionPercent(
              segment.startPosition,
              resolvedMaxPosition,
            );
            const bottom = positionPercent(
              segment.endPosition,
              resolvedMaxPosition,
            );
            const groups = groupsForSegment(
              zone.orchidGroups,
              segment,
              segments,
              index,
            );

            return (
              <div
                key={segment.id ?? `${segment.name}-${index}`}
                className="absolute inset-x-1 rounded-lg border border-transparent bg-transparent"
                style={{
                  top: `${top}%`,
                  height: `${Math.max(bottom - top, minimumHeightPercent())}%`,
                }}
              >
                <div className="h-full rounded-lg bg-[#f7faf7] px-2 py-2">
                  <div className="rounded-lg bg-[#eef8ef] px-2 py-1 text-[#2f8a45] shadow-sm">
                    <p className="text-sm font-bold">{segment.name}</p>
                    <p className="mt-0.5 text-sm font-semibold text-[#4a5950]">
                      {formatPosition(segment.startPosition)} -{" "}
                      {formatPosition(segment.endPosition)}
                    </p>
                  </div>

                  <div className="mt-2 flex flex-col gap-1">
                    {groups.map((orchidGroup) => (
                      <OrchidGroupBlock
                        key={orchidGroup.id}
                        compact
                        draggable={placementEditMode && !saving}
                        orchidGroup={orchidGroup}
                        selected={selectedOrchidGroupId === orchidGroup.id}
                        onDragEnd={onDragEnd}
                        onDragStart={() => onDragStart(orchidGroup.id)}
                        onSelect={() => onSelectOrchidGroup(orchidGroup.id)}
                      />
                    ))}
                  </div>
                </div>
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

function resolveMaxPosition(value: number | null) {
  return value && value > 0 ? value : 28;
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

function resolveSegments(
  profile: BedZonePlacementProfile | null,
  maxPosition: number,
  side: BedZone["side"],
) {
  if (profile?.segments.length) {
    return [...profile.segments].sort(
      (a, b) => a.startPosition - b.startPosition || a.sortOrder - b.sortOrder,
    );
  }

  return [
    {
      id: null,
      name: `${side === "LEFT" ? "A" : "B"} 구간`,
      segmentType: "CUSTOM" as const,
      sortOrder: 1,
      startPosition: 0,
      endPosition: maxPosition,
      memo: null,
      capacities: [],
    },
  ];
}

function groupsForSegment(
  orchidGroups: OrchidGroup[],
  segment: BedZoneSegment,
  segments: BedZoneSegment[],
  segmentIndex: number,
) {
  const matched = orchidGroups.filter((orchidGroup) =>
    orchidGroup.segmentPlacements.some(
      (placement) => placement.segmentId === segment.id,
    ),
  );

  if (matched.length > 0) {
    return matched;
  }

  if (segments.length === 1 || segmentIndex > 0) {
    return [];
  }

  return orchidGroups.filter(
    (orchidGroup) => orchidGroup.segmentPlacements.length === 0,
  );
}

function positionPercent(value: number, maxPosition: number) {
  return maxPosition === 0 ? 0 : (value / maxPosition) * 100;
}

function minimumHeightPercent() {
  return (FALLBACK_SEGMENT_HEIGHT / MAP_HEIGHT) * 100;
}

function formatPosition(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}
