"use client";

import type { ReactNode } from "react";
import type { AuctionTrackingSummary } from "@/entities/farm/types";
import {
  auctionStatusOptions,
  auctionStatusLabel,
} from "../../lib/auctionDisplay";
import type { AuctionFilterState } from "../../model/types";
import {
  FilterCheck,
  FilterDateRange,
  FilterInput,
  FilterSelect,
} from "@/shared/ui/FilterControls";
import { ListFilterToolbar } from "@/shared/ui/ListFilterToolbar";
import {
  periodFilterBadge,
  valueFilterBadge,
} from "@/shared/ui/listFilterBadges";

export function AuctionFilters({
  filters,
  appliedFilters,
  columnSettings,
  loading,
  summary,
  onChange,
  onRemove,
  onReset,
  onResetFilters,
  onSearch,
}: {
  filters: AuctionFilterState;
  appliedFilters: AuctionFilterState;
  columnSettings?: ReactNode;
  loading: boolean;
  summary: AuctionTrackingSummary;
  onChange: <K extends keyof AuctionFilterState>(
    field: K,
    value: AuctionFilterState[K],
  ) => void;
  onRemove: (fields: readonly (keyof AuctionFilterState)[]) => void;
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
    ...periodFilterBadge(
      appliedFilters.from,
      appliedFilters.to,
      ["from", "to"] as const,
      onRemove,
    ),
    ...valueFilterBadge(
      "market",
      `경매장: ${appliedFilters.market}`,
      appliedFilters.market,
      onRemove,
    ),
    ...valueFilterBadge(
      "variety",
      `품종명: ${appliedFilters.variety}`,
      appliedFilters.variety,
      onRemove,
    ),
    ...valueFilterBadge(
      "grade",
      `등급: ${appliedFilters.grade}`,
      appliedFilters.grade,
      onRemove,
    ),
    ...valueFilterBadge(
      "status",
      `상태: ${auctionStatusLabel(appliedFilters.status)}`,
      appliedFilters.status,
      onRemove,
    ),
    ...valueFilterBadge(
      "reviewOnly",
      "확인 필요만",
      appliedFilters.reviewOnly,
      onRemove,
    ),
    ...valueFilterBadge(
      "waitingOnly",
      "재경매 대기만",
      appliedFilters.waitingOnly,
      onRemove,
    ),
    ...valueFilterBadge(
      "returnOnly",
      "반환만",
      appliedFilters.returnOnly,
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
      searchPlaceholder="경매장, 품종, 메모"
      settingsControl={columnSettings}
      onKeywordChange={(value) => onChange("keyword", value)}
      onClearKeyword={() => onRemove(["keyword"])}
      onReset={onReset}
      onResetFilters={onResetFilters}
      onSearch={onSearch}
      checkboxFilters={
        <>
          <FilterCheck
            label={`확인 필요만 ${summary.reviewRequiredCount.toLocaleString()}건`}
            checked={filters.reviewOnly}
            onChange={(checked) => onChange("reviewOnly", checked)}
          />
          <FilterCheck
            label={`재경매 대기만 ${summary.waitingQuantity.toLocaleString()}분`}
            checked={filters.waitingOnly}
            onChange={(checked) => onChange("waitingOnly", checked)}
          />
        </>
      }
    >
      <FilterDateRange
        layout="row"
        from={filters.from}
        to={filters.to}
        onFromChange={(value) => onChange("from", value)}
        onToChange={(value) => onChange("to", value)}
      />
      <FilterInput
        layout="row"
        label="경매장"
        value={filters.market}
        onChange={(value) => onChange("market", value)}
      />
      <FilterInput
        layout="row"
        label="품종명"
        value={filters.variety}
        onChange={(value) => onChange("variety", value)}
      />
      <FilterInput
        layout="row"
        label="등급"
        value={filters.grade}
        onChange={(value) => onChange("grade", value)}
      />
      <FilterSelect
        layout="row"
        label="상태"
        value={filters.status}
        onChange={(value) =>
          onChange(
            "status",
            auctionStatusOptions.find(([status]) => status === value)?.[0] ??
              "",
          )
        }
      >
        <option value="">전체</option>
        {auctionStatusOptions.map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </FilterSelect>
    </ListFilterToolbar>
  );
}
