"use client";

import type { OrchidGroup } from "@/entities/farm/types";
import { OrchidGroupSearchPanel } from "@/entities/farm/ui/OrchidGroupSearchPanel";
import type { OrchidManagementSearchState } from "../../model/types";

export default function OrchidSearchPanel({
  currentHouseId,
  currentSelectedOrchidGroupId,
  filteredCount,
  filters,
  hasActiveSearch,
  loading,
  results,
  onClear,
  onSelectResult,
  onUpdateFilter,
}: {
  currentHouseId?: number;
  currentSelectedOrchidGroupId: number | null;
  filteredCount: number;
  filters: OrchidManagementSearchState;
  hasActiveSearch: boolean;
  loading: boolean;
  results: OrchidGroup[];
  onClear: () => void;
  onSelectResult: (orchidGroup: OrchidGroup) => void;
  onUpdateFilter: <K extends keyof OrchidManagementSearchState>(
    field: K,
    value: OrchidManagementSearchState[K],
  ) => void;
}) {
  return (
    <OrchidGroupSearchPanel
      currentHouseId={currentHouseId}
      currentSelectedOrchidGroupId={currentSelectedOrchidGroupId}
      filteredCount={filteredCount}
      filters={filters}
      hasActiveSearch={hasActiveSearch}
      loading={loading}
      placeholder="전체 농장 난 묶음 검색"
      resultDescription="결과가 있는 동, 배드, 구역, 난 묶음만 강조"
      results={results}
      showResultIndex
      onClear={onClear}
      onSelectResult={onSelectResult}
      onUpdateFilter={onUpdateFilter}
    />
  );
}
