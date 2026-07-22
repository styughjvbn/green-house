"use client";

import useEmblaCarousel from "embla-carousel-react";
import { useEffect, useRef } from "react";
import type { PhysicalBed, VisibleBedCount } from "@/entities/farm/types";
import type { MapCellRangePick, OrchidSelection } from "../../model/types";
import PhysicalBedBlock from "./PhysicalBedBlock";

export default function ContinuousBedMap({
  beds,
  startBedIndex,
  visibleBedCount,
  distinguishVarietyColors,
  filteredOrchidGroupIds,
  selectedOrchidGroupIds,
  selection,
  showScale,
  cellRangePick,
  onStartBedIndexChange,
  onPickCellRange,
  onSelectBedZone,
  onSelectPhysicalBed,
  onSelectOrchidGroup,
}: {
  beds: PhysicalBed[];
  startBedIndex: number;
  visibleBedCount: VisibleBedCount;
  distinguishVarietyColors: boolean;
  filteredOrchidGroupIds: Set<number>;
  selectedOrchidGroupIds: Set<number>;
  selection: OrchidSelection | null;
  showScale: boolean;
  cellRangePick: MapCellRangePick;
  onStartBedIndexChange: (index: number) => void;
  onPickCellRange: (bedZoneId: number, cell: number) => void;
  onSelectBedZone: (bedZoneId: number) => void;
  onSelectPhysicalBed: (physicalBedId: number) => void;
  onSelectOrchidGroup: (orchidGroupId: number) => void;
}) {
  const pointerStartRef = useRef<{ x: number; y: number } | null>(null);
  const pointerDraggedRef = useRef(false);
  const lastDragEndRef = useRef(0);
  const [emblaRef, emblaApi] = useEmblaCarousel({
    align: "start",
    loop: false,
    dragFree: false,
    slidesToScroll: 1,
    containScroll: "trimSnaps",
    startIndex: startBedIndex,
  });

  useEffect(() => {
    if (!emblaApi) return;
    const syncSelection = () =>
      onStartBedIndexChange(emblaApi.selectedScrollSnap());
    emblaApi.on("select", syncSelection);
    return () => {
      emblaApi.off("select", syncSelection);
    };
  }, [emblaApi, onStartBedIndexChange]);

  useEffect(() => {
    if (!emblaApi) return;
    emblaApi.reInit();
    emblaApi.scrollTo(startBedIndex, true);
  }, [emblaApi, startBedIndex, visibleBedCount]);

  const renderStartIndex = Math.max(0, startBedIndex - visibleBedCount);
  const renderEndIndex = Math.min(
    beds.length,
    startBedIndex + visibleBedCount * 2,
  );

  return (
    <section
      className="h-full min-h-0 overflow-hidden rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm"
      data-testid="map-root"
    >
      <div
        ref={emblaRef}
        className="h-full overflow-hidden"
        onClickCapture={(event) => {
          if (Date.now() - lastDragEndRef.current < 400) {
            event.preventDefault();
            event.stopPropagation();
          }
        }}
        onPointerDownCapture={(event) => {
          pointerStartRef.current = { x: event.clientX, y: event.clientY };
          pointerDraggedRef.current = false;
        }}
        onPointerMoveCapture={(event) => {
          const start = pointerStartRef.current;
          if (
            start &&
            Math.hypot(event.clientX - start.x, event.clientY - start.y) > 6
          ) {
            pointerDraggedRef.current = true;
          }
        }}
        onPointerUpCapture={() => {
          if (pointerDraggedRef.current) {
            lastDragEndRef.current = Date.now();
          }
          pointerStartRef.current = null;
          pointerDraggedRef.current = false;
        }}
        onPointerCancelCapture={() => {
          pointerStartRef.current = null;
          pointerDraggedRef.current = false;
        }}
      >
        <div className="-ml-3 flex h-full touch-pan-y">
          {beds.map((bed, index) => (
            <div
              key={bed.id}
              className="min-w-0 shrink-0 pl-3"
              style={{ flexBasis: `${100 / visibleBedCount}%` }}
            >
              {index >= renderStartIndex && index < renderEndIndex ? (
                <PhysicalBedBlock
                  bed={bed}
                  distinguishVarietyColors={distinguishVarietyColors}
                  filteredOrchidGroupIds={filteredOrchidGroupIds}
                  selectedOrchidGroupIds={selectedOrchidGroupIds}
                  selection={selection}
                  showScale={showScale}
                  cellRangePick={cellRangePick}
                  onPickCellRange={onPickCellRange}
                  onSelectBedZone={onSelectBedZone}
                  onSelectPhysicalBed={onSelectPhysicalBed}
                  onSelectOrchidGroup={onSelectOrchidGroup}
                />
              ) : (
                <div aria-hidden="true" className="h-full" />
              )}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
