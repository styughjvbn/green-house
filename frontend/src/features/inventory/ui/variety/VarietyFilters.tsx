"use client";

import type { ReactNode } from "react";
import { FilterSelect } from "@/shared/ui/FilterControls";
import { ListFilterToolbar } from "@/shared/ui/ListFilterToolbar";
import { valueFilterBadge } from "@/shared/ui/listFilterBadges";
import type { VarietyFilterState } from "../../model/types";

export function VarietyFilters({
  filters,
  appliedFilters,
  columnSettings,
  loading,
  genera,
  onChange,
  onRemove,
  onReset,
  onResetFilters,
  onSearch,
}: {
  filters: VarietyFilterState;
  appliedFilters: VarietyFilterState;
  columnSettings?: ReactNode;
  loading: boolean;
  genera: string[];
  onChange: <K extends keyof VarietyFilterState>(
    field: K,
    value: VarietyFilterState[K],
  ) => void;
  onRemove: (fields: readonly (keyof VarietyFilterState)[]) => void;
  onReset: () => void;
  onResetFilters: () => void;
  onSearch: () => void;
}) {
  const genusOptions = [...new Set(genera)];
  const appliedBadges = [
    ...valueFilterBadge(
      "keyword",
      `검색: ${appliedFilters.keyword.trim()}`,
      appliedFilters.keyword,
      onRemove,
    ),
    ...valueFilterBadge(
      "genus",
      `속: ${appliedFilters.genus}`,
      appliedFilters.genus,
      onRemove,
    ),
    ...valueFilterBadge(
      "status",
      `상태: ${appliedFilters.status === "ACTIVE" ? "활성" : "비활성"}`,
      appliedFilters.status,
      onRemove,
    ),
  ];
  return (
    <ListFilterToolbar
      activeCount={
        appliedBadges.filter((badge) => badge.key !== "keyword").length
      }
      appliedBadges={appliedBadges}
      appliedKeyword={appliedFilters.keyword}
      keyword={filters.keyword}
      loading={loading}
      searchPlaceholder="품종명을 입력하세요"
      settingsControl={columnSettings}
      onKeywordChange={(value) => onChange("keyword", value)}
      onClearKeyword={() => onRemove(["keyword"])}
      onReset={onReset}
      onResetFilters={onResetFilters}
      onSearch={onSearch}
    >
      <FilterSelect
        layout="row"
        label="속"
        value={filters.genus}
        onChange={(value) => onChange("genus", value)}
      >
        <option value="">전체</option>
        {genusOptions.map((value) => (
          <option key={value}>{value}</option>
        ))}
      </FilterSelect>
      <FilterSelect
        layout="row"
        label="상태"
        value={filters.status}
        onChange={(value) =>
          onChange("status", value as VarietyFilterState["status"])
        }
      >
        <option value="">전체</option>
        <option value="ACTIVE">활성</option>
        <option value="INACTIVE">비활성</option>
      </FilterSelect>
    </ListFilterToolbar>
  );
}
