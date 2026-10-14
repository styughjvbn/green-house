"use client";

import type { ReactNode } from "react";
import { FilterSelect } from "@/shared/ui/FilterControls";
import { ListFilterToolbar } from "@/shared/ui/ListFilterToolbar";
import { valueFilterBadge } from "@/shared/ui/listFilterBadges";
import type { InboundFilterState } from "../../model/types";
import {
  INBOUND_STATUS_LABELS,
  INBOUND_TYPE_LABELS,
} from "../../lib/inboundUi";

export function InboundFilters({
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
  filters: InboundFilterState;
  appliedFilters: InboundFilterState;
  columnSettings?: ReactNode;
  loading: boolean;
  onChange: <K extends keyof InboundFilterState>(
    field: K,
    value: InboundFilterState[K],
  ) => void;
  onRemove: (fields: readonly (keyof InboundFilterState)[]) => void;
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
      "inboundType",
      `입고 유형: ${appliedFilters.inboundType ? INBOUND_TYPE_LABELS[appliedFilters.inboundType] : ""}`,
      appliedFilters.inboundType,
      onRemove,
    ),
    ...valueFilterBadge(
      "status",
      `상태: ${appliedFilters.status ? INBOUND_STATUS_LABELS[appliedFilters.status] : ""}`,
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
      searchPlaceholder="품종명, 위치"
      settingsControl={columnSettings}
      onKeywordChange={(value) => onChange("keyword", value)}
      onClearKeyword={() => onRemove(["keyword"])}
      onReset={onReset}
      onResetFilters={onResetFilters}
      onSearch={onSearch}
    >
      <FilterSelect
        layout="row"
        label="입고 유형"
        value={filters.inboundType}
        onChange={(value) =>
          onChange("inboundType", value as InboundFilterState["inboundType"])
        }
      >
        <option value="">전체</option>
        {Object.entries(INBOUND_TYPE_LABELS).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </FilterSelect>
      <FilterSelect
        layout="row"
        label="상태"
        value={filters.status}
        onChange={(value) =>
          onChange("status", value as InboundFilterState["status"])
        }
      >
        <option value="">전체</option>
        {Object.entries(INBOUND_STATUS_LABELS).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </FilterSelect>
    </ListFilterToolbar>
  );
}
