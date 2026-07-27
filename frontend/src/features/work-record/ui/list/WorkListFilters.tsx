import type { WorkOperationStatus } from "@/entities/farm/types";
import {
  FilterDateRange,
  FilterGrid,
  FilterInput,
  FilterPanel,
  FilterResetButton,
  FilterSearchButton,
  FilterSelect,
} from "@/shared/ui/FilterControls";
import type { WorkOperationFilterState } from "../../model/useWorkOperations";

export function WorkListFilters({
  allStatusLabel = "관리 대상 전체",
  filters,
  loading,
  onChange,
  onReset,
  onSearch,
}: {
  allStatusLabel?: string;
  filters: WorkOperationFilterState;
  loading: boolean;
  onChange: <K extends keyof WorkOperationFilterState>(
    field: K,
    value: WorkOperationFilterState[K],
  ) => void;
  onReset: () => void;
  onSearch: () => void;
}) {
  return (
    <FilterPanel>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          onSearch();
        }}
      >
        <FilterGrid className="sm:grid-cols-2 lg:grid-cols-[1.4fr_0.7fr_1.2fr_auto_auto]">
          <FilterDateRange
            from={filters.from}
            to={filters.to}
            onFromChange={(value) => onChange("from", value)}
            onToChange={(value) => onChange("to", value)}
          />
          <FilterSelect
            label="상태"
            value={filters.status}
            onChange={(value) =>
              onChange("status", value as WorkOperationStatus | "")
            }
          >
            <option value="">{allStatusLabel}</option>
            <option value="PLANNED">계획</option>
            <option value="IN_PROGRESS">진행 중</option>
            <option value="PAUSED">일시중지</option>
            <option value="COMPLETED">완료</option>
            <option value="CORRECTED">보정됨</option>
            <option value="CANCELED">취소</option>
          </FilterSelect>
          <FilterInput
            label="키워드"
            placeholder="작업명, 유형, 작업자, 메모"
            value={filters.keyword}
            onChange={(value) => onChange("keyword", value)}
          />
          <FilterResetButton onClick={onReset} />
          <FilterSearchButton disabled={loading} label="조회" />
        </FilterGrid>
      </form>
    </FilterPanel>
  );
}
