"use client";

import { useEffect, useMemo, useState } from "react";
import type { House, OrchidGroup } from "@/entities/farm/types";
import { searchOrchidGroups } from "../api/orchidManagementApi";
import type { OrchidManagementSearchState } from "./types";

export function useOrchidManagementSearch(
  house: House,
  initialFilters?: OrchidManagementSearchState,
) {
  const [filters, setFilters] = useState<OrchidManagementSearchState>({
    keyword: initialFilters?.keyword ?? "",
    status: initialFilters?.status ?? "",
  });
  const [results, setResults] = useState<OrchidGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const currentHouseOrchidGroupIds = useMemo(
    () =>
      new Set(
        house.physicalBeds.flatMap((bed) =>
          bed.bedZones.flatMap((zone) =>
            zone.orchidGroups.map((orchidGroup) => orchidGroup.id),
          ),
        ),
      ),
    [house],
  );
  const active = filters.keyword.trim().length > 0;
  const filteredOrchidGroupIds = useMemo(() => {
    if (!active) return currentHouseOrchidGroupIds;
    return new Set(
      results
        .filter((orchidGroup) => currentHouseOrchidGroupIds.has(orchidGroup.id))
        .map((orchidGroup) => orchidGroup.id),
    );
  }, [active, currentHouseOrchidGroupIds, results]);

  useEffect(() => {
    let ignore = false;

    async function loadResults() {
      if (!active) {
        setResults([]);
        setLoading(false);
        return;
      }

      setLoading(true);
      try {
        const nextResults = await searchOrchidGroups(filters);
        if (!ignore) setResults(nextResults);
      } catch {
        if (!ignore) setResults([]);
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    const timeout = window.setTimeout(() => void loadResults(), 250);
    return () => {
      ignore = true;
      window.clearTimeout(timeout);
    };
  }, [active, filters]);

  function updateFilter<K extends keyof OrchidManagementSearchState>(
    field: K,
    value: OrchidManagementSearchState[K],
  ) {
    setFilters((current) => ({ ...current, [field]: value }));
  }

  return {
    active,
    filteredOrchidGroupIds,
    filters,
    loading,
    results,
    updateFilter,
  };
}
