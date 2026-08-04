"use client";

import { useQueries, useQuery } from "@tanstack/react-query";
import { useCallback, useMemo, useState } from "react";
import { fetchApi } from "@/shared/api/client";
import type { OrchidManagementViewport, PhysicalBed } from "../types";

const VISIBLE_BED_COUNT = 3;
const BUFFER_BED_COUNT = 3;

export function useFarmBedViewportCache(initialBedId: number | null) {
  const [viewportIndex, setViewportIndex] = useState(0);
  const initialViewport = useQuery(farmBedViewportQueryOptions(initialBedId));
  const bedOrder = initialViewport.data?.bedOrder ?? [];
  const viewportStartBedIds = useMemo(
    () => bufferStartBedIds(bedOrder, viewportIndex),
    [bedOrder, viewportIndex],
  );
  const bufferedViewports = useQueries({
    queries: viewportStartBedIds.map((bedId) =>
      farmBedViewportQueryOptions(bedId),
    ),
  });
  const bedsById = useMemo(() => {
    const beds = new Map<number, PhysicalBed>();
    [initialViewport.data, ...bufferedViewports.map((query) => query.data)]
      .filter(
        (viewport): viewport is OrchidManagementViewport => viewport != null,
      )
      .forEach((viewport) => {
        viewport.beds.forEach((bed) => beds.set(bed.id, bed));
      });
    return beds;
  }, [bufferedViewports, initialViewport.data]);

  const loadAround = useCallback((index: number) => {
    setViewportIndex(index);
  }, []);

  return {
    bedOrder,
    bedsById,
    loadAround,
    loading:
      initialViewport.isLoading ||
      bufferedViewports.some((query) => query.isLoading),
  };
}

function farmBedViewportQueryOptions(startBedId: number | null) {
  return {
    queryKey: [
      "farm-status",
      "orchid-management-viewport",
      startBedId,
      VISIBLE_BED_COUNT,
    ] as const,
    queryFn: () => getFarmBedViewport(startBedId),
    staleTime: 30_000,
  };
}

function getFarmBedViewport(startBedId: number | null) {
  const params = new URLSearchParams({
    bedCount: String(VISIBLE_BED_COUNT),
  });
  if (startBedId != null) params.set("startBedId", String(startBedId));
  return fetchApi<OrchidManagementViewport>(
    `/farm-status/orchid-management?${params.toString()}`,
  );
}

function bufferStartBedIds(
  bedOrder: OrchidManagementViewport["bedOrder"],
  viewportIndex: number,
) {
  if (bedOrder.length === 0) return [];
  return [...new Set([-BUFFER_BED_COUNT, 0, BUFFER_BED_COUNT])]
    .map((offset) => viewportIndex + offset)
    .map((index) => Math.max(0, Math.min(index, bedOrder.length - 1)))
    .map((index) => bedOrder[index]?.id)
    .filter((bedId): bedId is number => bedId != null);
}
