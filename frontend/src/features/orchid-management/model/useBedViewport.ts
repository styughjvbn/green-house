"use client";

import { useQueries } from "@tanstack/react-query";
import { useCallback, useMemo, useState } from "react";
import type {
  OrchidManagementBedOrderItem,
  OrchidManagementViewport,
  VisibleBedCount,
} from "@/entities/farm/types";
import { getOrchidManagementViewport } from "../api/orchidManagementApi";
import {
  clampBedViewportIndex,
  mergeViewportRequestKeys,
  viewportRequestKeys,
} from "../lib/bedViewportUtils";
import type { BedViewportState } from "./bedViewportTypes";

export function useBedViewport(
  initialViewport: OrchidManagementViewport,
  bedOrder: OrchidManagementBedOrderItem[],
) {
  const initialIndex = Math.max(
    0,
    bedOrder.findIndex((bed) => bed.id === initialViewport.startBedId),
  );
  const [startBedIndex, setStartBedIndex] = useState(() =>
    clampBedViewportIndex(initialIndex, bedOrder.length),
  );
  const [visibleBedCount, setVisibleBedCountState] = useState(
    initialViewport.bedCount,
  );
  const [requestedViewports, setRequestedViewports] = useState(() =>
    viewportRequestKeys(bedOrder, initialIndex, initialViewport.bedCount),
  );
  const viewportQueries = useQueries({
    queries: requestedViewports.map(({ startBedId, bedCount }) => ({
      queryKey: orchidManagementViewportQueryKey(startBedId, bedCount),
      queryFn: () => getOrchidManagementViewport(startBedId, bedCount),
      initialData:
        startBedId === initialViewport.startBedId &&
        bedCount === initialViewport.bedCount
          ? initialViewport
          : undefined,
      staleTime: 30_000,
    })),
  });
  const bedsById = useMemo(() => {
    const beds = new Map<number, OrchidManagementViewport["beds"][number]>();
    viewportQueries.forEach((query) => {
      query.data?.beds.forEach((bed) => beds.set(bed.id, bed));
    });
    return beds;
  }, [viewportQueries]);

  const requestAround = useCallback(
    (index: number, count: VisibleBedCount) => {
      const nextKeys = viewportRequestKeys(bedOrder, index, count);
      setRequestedViewports((current) =>
        mergeViewportRequestKeys(current, nextKeys),
      );
    },
    [bedOrder],
  );

  const replaceUrl = useCallback(
    (nextIndex: number, nextCount: VisibleBedCount) => {
      const startBed = bedOrder[nextIndex];
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
    [bedOrder],
  );

  const setStartIndex = useCallback(
    (requestedIndex: number) => {
      const nextIndex = clampBedViewportIndex(requestedIndex, bedOrder.length);
      setStartBedIndex(nextIndex);
      requestAround(nextIndex, visibleBedCount);
      replaceUrl(nextIndex, visibleBedCount);
    },
    [bedOrder.length, replaceUrl, requestAround, visibleBedCount],
  );

  const setVisibleBedCount = useCallback(
    (nextCount: VisibleBedCount) => {
      const nextIndex = clampBedViewportIndex(startBedIndex, bedOrder.length);
      setVisibleBedCountState(nextCount);
      setStartBedIndex(nextIndex);
      requestAround(nextIndex, nextCount);
      replaceUrl(nextIndex, nextCount);
    },
    [bedOrder.length, replaceUrl, requestAround, startBedIndex],
  );

  const state = useMemo<BedViewportState>(() => {
    const visibleBedOrder = bedOrder.slice(
      startBedIndex,
      startBedIndex + visibleBedCount,
    );
    const visibleBeds = visibleBedOrder
      .map((bed) => bedsById.get(bed.id))
      .filter((bed) => bed != null);
    const currentHouseId = bedOrder[startBedIndex]?.houseId;
    const houseIds = bedOrder.reduce<number[]>((ids, bed) => {
      if (ids.at(-1) !== bed.houseId) ids.push(bed.houseId);
      return ids;
    }, []);
    const currentHouseIndex =
      currentHouseId == null ? -1 : houseIds.indexOf(currentHouseId);
    return {
      startBedId: visibleBeds[0]?.id ?? null,
      startBedIndex,
      visibleBedCount,
      visibleBedIds: visibleBedOrder.map((bed) => bed.id),
      visibleBeds,
      hasPreviousHouse: currentHouseIndex > 0,
      hasNextHouse:
        currentHouseIndex >= 0 && currentHouseIndex < houseIds.length - 1,
    };
  }, [bedOrder, bedsById, startBedIndex, visibleBedCount]);

  const loadedBeds = useMemo(
    () =>
      bedOrder.map((bed) => bedsById.get(bed.id)).filter((bed) => bed != null),
    [bedOrder, bedsById],
  );

  return {
    ...state,
    bedOrder,
    bedsById,
    loadedBeds,
    loading: state.visibleBedIds.some((bedId) => !bedsById.has(bedId)),
    actions: {
      previousHouse: () => {
        const currentHouseId = bedOrder[startBedIndex]?.houseId;
        let previousIndex = startBedIndex - 1;
        while (
          previousIndex >= 0 &&
          bedOrder[previousIndex]?.houseId === currentHouseId
        ) {
          previousIndex -= 1;
        }
        const previousHouseId = bedOrder[previousIndex]?.houseId;
        while (
          previousIndex > 0 &&
          bedOrder[previousIndex - 1]?.houseId === previousHouseId
        ) {
          previousIndex -= 1;
        }
        if (previousIndex >= 0) setStartIndex(previousIndex);
      },
      nextHouse: () => {
        const currentHouseId = bedOrder[startBedIndex]?.houseId;
        const nextIndex = bedOrder.findIndex(
          (bed, index) =>
            index > startBedIndex && bed.houseId !== currentHouseId,
        );
        if (nextIndex >= 0) setStartIndex(nextIndex);
      },
      goToBed: (bedId: number) => {
        const index = bedOrder.findIndex((bed) => bed.id === bedId);
        if (index >= 0) setStartIndex(index);
      },
      goToHouse: (houseId: number) => {
        const index = bedOrder.findIndex((bed) => bed.houseId === houseId);
        if (index >= 0) setStartIndex(index);
      },
      setStartIndex,
      setVisibleBedCount,
    },
  };
}

export function orchidManagementViewportQueryKey(
  startBedId: number,
  bedCount: VisibleBedCount,
) {
  return [
    "farm-status",
    "orchid-management-viewport",
    startBedId,
    bedCount,
  ] as const;
}
