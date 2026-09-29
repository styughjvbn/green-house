"use client";

import type { OrchidGroup } from "@/entities/farm/types";
import {
  OrchidGroupSearchPanel,
  type OrchidSearchGroupOption,
} from "@/entities/farm/ui/OrchidGroupSearchPanel";
import type {
  FarmStatusSearchGroup,
  FarmStatusSearchState,
} from "../../model/types";

export function FarmStatusSearchPanel({
  currentSelectedOrchidGroupId,
  filters,
  hasActiveSearch,
  loading,
  groupError,
  groupLoading,
  groupSelectionPending,
  groups,
  results,
  selectedGroupKey,
  onClear,
  onSelectGroup,
  onSelectResult,
  onUpdateFilter,
}: {
  currentSelectedOrchidGroupId: number | null;
  filters: FarmStatusSearchState;
  hasActiveSearch: boolean;
  loading: boolean;
  groupError: string | null;
  groupLoading: boolean;
  groupSelectionPending: boolean;
  groups: FarmStatusSearchGroup[];
  results: OrchidGroup[];
  selectedGroupKey: string | null;
  onClear: () => void;
  onSelectGroup: (group: OrchidSearchGroupOption) => void;
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
      groupError={groupError}
      groupLoading={groupLoading}
      groupSelectionPending={groupSelectionPending}
      groups={groups}
      placeholder="전체 농장 난 묶음 검색"
      resultDescription="결과가 있는 곳만 강조"
      results={results}
      selectedGroupKey={selectedGroupKey}
      showResultIndex
      statuses={["정상", "주의", "이상", "병해충"]}
      variant="soft"
      onClear={onClear}
      onSelectGroup={onSelectGroup}
      onSelectResult={onSelectResult}
      onUpdateFilter={onUpdateFilter}
    />
  );
}
