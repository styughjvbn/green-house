"use client";

import { useCallback, useMemo, useState } from "react";
import type { PhysicalBed, VisibleBedCount } from "@/entities/farm/types";
import type { BedViewportState } from "./bedViewportTypes";

export function useBedViewport(
  beds: PhysicalBed[],
  initialStartBedId: number | null,
  initialVisibleBedCount: VisibleBedCount,
) {
  const initialIndex = Math.max(
    0,
    beds.findIndex((bed) => bed.id === initialStartBedId),
  );
  const [startBedIndex, setStartBedIndex] = useState(() =>
    clampStartIndex(initialIndex, beds.length, initialVisibleBedCount),
  );
  const [visibleBedCount, setVisibleBedCountState] = useState(
    initialVisibleBedCount,
  );

  const replaceUrl = useCallback(
    (nextIndex: number, nextCount: VisibleBedCount) => {
      const startBed = beds[nextIndex];
      const url = new URL(window.location.href);
      const params = url.searchParams;
      params.delete("houseId");
      if (startBed) params.set("startBedId", String(startBed.id));
      else params.delete("startBedId");
      params.set("bedCount", String(nextCount));
      const query = params.toString();
      window.history.replaceState(
        window.history.state,
        "",
        `${url.pathname}${query ? `?${query}` : ""}${url.hash}`,
      );
    },
    [beds],
  );

  const setStartIndex = useCallback(
    (requestedIndex: number) => {
      const nextIndex = clampStartIndex(
        requestedIndex,
        beds.length,
        visibleBedCount,
      );
      setStartBedIndex(nextIndex);
      replaceUrl(nextIndex, visibleBedCount);
    },
    [beds.length, replaceUrl, visibleBedCount],
  );

  const setVisibleBedCount = useCallback(
    (nextCount: VisibleBedCount) => {
      const nextIndex = clampStartIndex(startBedIndex, beds.length, nextCount);
      setVisibleBedCountState(nextCount);
      setStartBedIndex(nextIndex);
      replaceUrl(nextIndex, nextCount);
    },
    [beds.length, replaceUrl, startBedIndex],
  );

  const state = useMemo<BedViewportState>(() => {
    const visibleBeds = beds.slice(
      startBedIndex,
      startBedIndex + visibleBedCount,
    );
    return {
      startBedId: visibleBeds[0]?.id ?? null,
      startBedIndex,
      visibleBedCount,
      visibleBedIds: visibleBeds.map((bed) => bed.id),
      visibleBeds,
      hasPrevious: startBedIndex > 0,
      hasNext: startBedIndex + visibleBedCount < beds.length,
    };
  }, [beds, startBedIndex, visibleBedCount]);

  return {
    ...state,
    actions: {
      previous: () => setStartIndex(startBedIndex - 1),
      next: () => setStartIndex(startBedIndex + 1),
      goToBed: (bedId: number) => {
        const index = beds.findIndex((bed) => bed.id === bedId);
        if (index >= 0) setStartIndex(index);
      },
      goToHouse: (houseId: number) => {
        const index = beds.findIndex((bed) => bed.houseId === houseId);
        if (index >= 0) setStartIndex(index);
      },
      setStartIndex,
      setVisibleBedCount,
    },
  };
}

function clampStartIndex(
  index: number,
  bedLength: number,
  visibleBedCount: VisibleBedCount,
) {
  return Math.min(Math.max(index, 0), Math.max(0, bedLength - visibleBedCount));
}
