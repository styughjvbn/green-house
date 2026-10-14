"use client";

import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { businessPartnerOptionQueryOptions } from "../../model/salesQueryOptions";
import { BusinessPartnerSelect } from "../common/BusinessPartnerSelect";
import type { SalesFilterState } from "../../model/types";
import { FilterDateRange, FilterSelect } from "@/shared/ui/FilterControls";
import { ListFilterToolbar } from "@/shared/ui/ListFilterToolbar";
import {
  periodFilterBadge,
  valueFilterBadge,
} from "@/shared/ui/listFilterBadges";

export function SalesFilters({
  filters,
  appliedFilters,
  columnSettings,
  loading,
  onSalesTypeChange,
  onChange,
  onRemove,
  onReset,
  onResetFilters,
  onSearch,
}: {
  filters: SalesFilterState;
  appliedFilters: SalesFilterState;
  columnSettings?: ReactNode;
  loading: boolean;
  onSalesTypeChange: (value: SalesFilterState["salesType"]) => void;
  onChange: <K extends keyof SalesFilterState>(
    field: K,
    value: SalesFilterState[K],
  ) => void;
  onRemove: (fields: readonly (keyof SalesFilterState)[]) => void;
  onReset: () => void;
  onResetFilters: () => void;
  onSearch: () => void;
}) {
  const partnerQuery = useQuery({
    ...businessPartnerOptionQueryOptions(Number(appliedFilters.partnerId)),
    enabled: appliedFilters.partnerId !== "",
  });
  const salesTypeLabel =
    appliedFilters.salesType === "AUCTION" ? "경매" : "일반 판매";
  const statusLabel =
    appliedFilters.salesStatus === "작성중"
      ? "대기"
      : appliedFilters.salesStatus;
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
      "salesType",
      `판매 유형: ${salesTypeLabel}`,
      appliedFilters.salesType,
      onRemove,
    ),
    ...valueFilterBadge(
      "partnerId",
      `거래처: ${partnerQuery.data?.name ?? `#${appliedFilters.partnerId}`}`,
      appliedFilters.partnerId,
      onRemove,
    ),
    ...valueFilterBadge(
      "salesStatus",
      `출고·출하: ${statusLabel}`,
      appliedFilters.salesStatus,
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
      searchPlaceholder="전표 ID, 거래처명, 메모"
      settingsControl={columnSettings}
      onKeywordChange={(value) => onChange("keyword", value)}
      onClearKeyword={() => onRemove(["keyword"])}
      onReset={onReset}
      onResetFilters={onResetFilters}
      onSearch={onSearch}
    >
      <FilterDateRange
        layout="row"
        from={filters.from}
        to={filters.to}
        onFromChange={(value) => onChange("from", value)}
        onToChange={(value) => onChange("to", value)}
      />
      <FilterSelect
        layout="row"
        label="판매 유형"
        value={filters.salesType}
        onChange={(value) =>
          onSalesTypeChange(value as SalesFilterState["salesType"])
        }
      >
        <option value="">전체</option>
        <option value="DIRECT">일반 판매</option>
        <option value="AUCTION">경매</option>
      </FilterSelect>
      <BusinessPartnerSelect
        layout="row"
        key={filters.salesType}
        auctionHouse={
          filters.salesType === "" ? undefined : filters.salesType === "AUCTION"
        }
        label="거래처"
        value={filters.partnerId}
        onChange={(value) => onChange("partnerId", value)}
      />
      <FilterSelect
        layout="row"
        label="출고·출하"
        value={filters.salesStatus}
        onChange={(value) => onChange("salesStatus", value)}
      >
        <option value="">전체</option>
        <option value="작성중">대기</option>
        <option value="완료">완료</option>
        <option value="취소">취소</option>
      </FilterSelect>
    </ListFilterToolbar>
  );
}
