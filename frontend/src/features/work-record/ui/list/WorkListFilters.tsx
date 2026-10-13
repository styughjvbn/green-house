import type { ReactNode } from "react";
import type { WorkOperationStatus } from "@/entities/farm/types";
import { FilterDateRange, FilterSelect } from "@/shared/ui/FilterControls";
import { ListFilterToolbar } from "@/shared/ui/ListFilterToolbar";
import type { WorkOperationFilterState } from "../../model/types";
import type {
  WorkWorkspaceFilterState,
  WorkWorkspaceScope,
  WorkFilterBadgeKey,
} from "../../lib/workRecordUrlState";
import { WorkManagementFilter } from "../WorkManagementFilter";
import { operationStatusLabel } from "../common/workOperationLabels";

export function WorkListFilters({
  appliedFilters,
  appliedScope,
  columnSettings,
  filters,
  loading,
  viewControl,
  onChange,
  onRemove,
  onReset,
  onResetFilters,
  onSearch,
}: {
  appliedFilters: WorkOperationFilterState;
  appliedScope: WorkWorkspaceScope;
  columnSettings: ReactNode;
  filters: WorkWorkspaceFilterState;
  loading: boolean;
  viewControl?: ReactNode;
  onChange: <K extends keyof WorkWorkspaceFilterState>(
    field: K,
    value: WorkWorkspaceFilterState[K],
  ) => void;
  onReset: () => void;
  onResetFilters: () => void;
  onRemove: (key: WorkFilterBadgeKey) => void;
  onSearch: () => void;
}) {
  const appliedBadges = [
    appliedFilters.keyword.trim()
      ? {
          key: "keyword",
          label: `검색: ${appliedFilters.keyword.trim()}`,
          onRemove: () => onRemove("keyword"),
        }
      : null,
    appliedFilters.from || appliedFilters.to
      ? {
          key: "period",
          onRemove: () => onRemove("period"),
          label: `기간: ${
            appliedFilters.from && appliedFilters.to
              ? `${appliedFilters.from} ~ ${appliedFilters.to}`
              : appliedFilters.from
                ? `${appliedFilters.from}부터`
                : `${appliedFilters.to}까지`
          }`,
        }
      : null,
    appliedFilters.status
      ? {
          key: "status",
          onRemove: () => onRemove("status"),
          label: `상태: ${operationStatusLabel(appliedFilters.status)}`,
        }
      : null,
    appliedScope === "MANAGEMENT"
      ? {
          key: "scope",
          label: "미완료 또는 오늘 업데이트 된 작업",
          onRemove: () => onRemove("scope"),
        }
      : null,
  ].filter((badge) => badge !== null);
  const activeCount =
    Number(Boolean(appliedFilters.from || appliedFilters.to)) +
    Number(Boolean(appliedFilters.status)) +
    Number(appliedScope === "MANAGEMENT");

  return (
    <ListFilterToolbar
      activeCount={activeCount}
      appliedBadges={appliedBadges}
      appliedKeyword={appliedFilters.keyword}
      checkboxFilters={
        <WorkManagementFilter
          scope={filters.scope}
          onChange={(scope) => onChange("scope", scope)}
        />
      }
      keyword={filters.keyword}
      leadingControls={viewControl}
      loading={loading}
      searchPlaceholder="작업 검색"
      settingsControl={columnSettings}
      onClearKeyword={() => onRemove("keyword")}
      onKeywordChange={(keyword) => onChange("keyword", keyword)}
      onReset={onReset}
      onResetFilters={onResetFilters}
      onSearch={onSearch}
    >
      <FilterDateRange
        from={filters.from}
        layout="row"
        to={filters.to}
        onFromChange={(from) => onChange("from", from)}
        onToChange={(to) => onChange("to", to)}
      />
      <FilterSelect
        label="작업 상태"
        layout="row"
        value={filters.status}
        onChange={(status) =>
          onChange("status", status as WorkOperationStatus | "")
        }
      >
        <option value="">모든 상태</option>
        <option value="PLANNED">계획</option>
        <option value="IN_PROGRESS">진행 중</option>
        <option value="PAUSED">일시중지</option>
        <option value="COMPLETED">완료</option>
        <option value="STOPPED">종료</option>
        <option value="CANCELED">취소</option>
      </FilterSelect>
    </ListFilterToolbar>
  );
}
