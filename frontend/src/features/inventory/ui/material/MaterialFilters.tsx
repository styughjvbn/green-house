"use client";

import type { ReactNode } from "react";
import { FilterInput, FilterSelect } from "@/shared/ui/FilterControls";
import { ListFilterToolbar } from "@/shared/ui/ListFilterToolbar";
import { valueFilterBadge } from "@/shared/ui/listFilterBadges";
import type { MaterialFilterState } from "../../model/types";

export function MaterialFilters({
  filters,
  appliedFilters,
  columnSettings,
  loading,
  onChange,
  onRemove,
  onReset,
  onResetFilters,
  onSearch,
}: {
  filters: MaterialFilterState;
  appliedFilters: MaterialFilterState;
  columnSettings?: ReactNode;
  loading: boolean;
  onChange: <K extends keyof MaterialFilterState>(
    field: K,
    value: MaterialFilterState[K],
  ) => void;
  onRemove: (fields: readonly (keyof MaterialFilterState)[]) => void;
  onReset: () => void;
  onResetFilters: () => void;
  onSearch: () => void;
}) {
  const appliedBadges = [
    ...valueFilterBadge(
      "keyword",
      `검색: ${appliedFilters.keyword.trim()}`,
      appliedFilters.keyword,
      onRemove,
    ),
    ...valueFilterBadge(
      "category",
      `자재 종류: ${appliedFilters.category}`,
      appliedFilters.category,
      onRemove,
    ),
    ...valueFilterBadge(
      "manufacturer",
      `제조사: ${appliedFilters.manufacturer}`,
      appliedFilters.manufacturer,
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
      searchPlaceholder="자재명을 입력하세요"
      settingsControl={columnSettings}
      onKeywordChange={(value) => onChange("keyword", value)}
      onClearKeyword={() => onRemove(["keyword"])}
      onReset={onReset}
      onResetFilters={onResetFilters}
      onSearch={onSearch}
    >
      <FilterSelect
        layout="row"
        label="자재 종류"
        value={filters.category}
        onChange={(value) => onChange("category", value)}
      >
        <option value="">전체</option>
        {["자재", "농약", "비료"].map((value) => (
          <option key={value}>{value}</option>
        ))}
      </FilterSelect>
      <FilterInput
        layout="row"
        label="제조사"
        placeholder="제조사를 입력하세요"
        value={filters.manufacturer}
        onChange={(value) => onChange("manufacturer", value)}
      />
      <FilterSelect
        layout="row"
        label="상태"
        value={filters.status}
        onChange={(value) =>
          onChange("status", value as MaterialFilterState["status"])
        }
      >
        <option value="">전체</option>
        <option value="ACTIVE">활성</option>
        <option value="INACTIVE">비활성</option>
      </FilterSelect>
    </ListFilterToolbar>
  );
}
