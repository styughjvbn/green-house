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
    clampStartIndex(initialIndex, beds.length),
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
      const nextIndex = clampStartIndex(requestedIndex, beds.length);
      setStartBedIndex(nextIndex);
      replaceUrl(nextIndex, visibleBedCount);
    },
    [beds.length, replaceUrl, visibleBedCount],
  );

  const setVisibleBedCount = useCallback(
    (nextCount: VisibleBedCount) => {
      const nextIndex = clampStartIndex(startBedIndex, beds.length);
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
    const currentHouseId = beds[startBedIndex]?.houseId;
    const houseIds = beds.reduce<number[]>((ids, bed) => {
      if (ids.at(-1) !== bed.houseId) ids.push(bed.houseId);
      return ids;
    }, []);
    const currentHouseIndex =
      currentHouseId == null ? -1 : houseIds.indexOf(currentHouseId);
    return {
      startBedId: visibleBeds[0]?.id ?? null,
      startBedIndex,
      visibleBedCount,
      visibleBedIds: visibleBeds.map((bed) => bed.id),
      visibleBeds,
      hasPreviousHouse: currentHouseIndex > 0,
      hasNextHouse:
        currentHouseIndex >= 0 && currentHouseIndex < houseIds.length - 1,
    };
  }, [beds, startBedIndex, visibleBedCount]);

  return {
    ...state,
    actions: {
      previousHouse: () => {
        const currentHouseId = beds[startBedIndex]?.houseId;
        let previousIndex = startBedIndex - 1;
        while (
          previousIndex >= 0 &&
          beds[previousIndex]?.houseId === currentHouseId
        ) {
          previousIndex -= 1;
        }
        const previousHouseId = beds[previousIndex]?.houseId;
        while (
          previousIndex > 0 &&
          beds[previousIndex - 1]?.houseId === previousHouseId
        ) {
          previousIndex -= 1;
        }
        if (previousIndex >= 0) setStartIndex(previousIndex);
      },
      nextHouse: () => {
        const currentHouseId = beds[startBedIndex]?.houseId;
        const nextIndex = beds.findIndex(
          (bed, index) =>
            index > startBedIndex && bed.houseId !== currentHouseId,
        );
        if (nextIndex >= 0) setStartIndex(nextIndex);
      },
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

function clampStartIndex(index: number, bedLength: number) {
  return Math.min(Math.max(index, 0), Math.max(0, bedLength - 1));
}
