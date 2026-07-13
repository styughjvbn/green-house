"use client";

import type { OrchidGroup } from "@/entities/farm/types";
import { OrchidGroupSearchPanel } from "@/entities/farm/ui/OrchidGroupSearchPanel";
import type { FarmStatusSearchState } from "../../model/types";

export function FarmStatusSearchPanel({
  currentSelectedOrchidGroupId,
  filters,
  hasActiveSearch,
  loading,
  results,
  onClear,
  onSelectResult,
  onUpdateFilter,
}: {
  currentSelectedOrchidGroupId: number | null;
  filters: FarmStatusSearchState;
  hasActiveSearch: boolean;
  loading: boolean;
  results: OrchidGroup[];
  onClear: () => void;
  onSelectResult: (orchidGroup: OrchidGroup) => void;
  onUpdateFilter: <K extends keyof FarmStatusSearchState>(
    field: K,
    value: FarmStatusSearchState[K],
  ) => void;
}) {
  return (
    <OrchidGroupSearchPanel
      currentSelectedOrchidGroupId={currentSelectedOrchidGroupId}
      filters={filters}
      hasActiveSearch={hasActiveSearch}
      loading={loading}
      placeholder="전체 농장 난 묶음 검색"
      resultDescription="결과가 있는 동, 배드, 구역, 난 묶음만 강조"
      results={results}
      statuses={["정상", "주의", "이상", "병해충"]}
      variant="soft"
      onClear={onClear}
      onSelectResult={onSelectResult}
      onUpdateFilter={onUpdateFilter}
    />
  );
}
