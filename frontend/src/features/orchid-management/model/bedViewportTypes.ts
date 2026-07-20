import type { PhysicalBed, VisibleBedCount } from "@/entities/farm/types";

export type BedViewportState = {
  startBedId: number | null;
  startBedIndex: number;
  visibleBedCount: VisibleBedCount;
  visibleBedIds: number[];
  visibleBeds: PhysicalBed[];
  hasPrevious: boolean;
  hasNext: boolean;
};
