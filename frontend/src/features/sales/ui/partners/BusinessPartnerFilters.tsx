"use client";

import type { ReactNode } from "react";
import type { BusinessPartnerFilterState } from "../../model/types";
import { FilterSelect } from "@/shared/ui/FilterControls";
import { ListFilterToolbar } from "@/shared/ui/ListFilterToolbar";
import { valueFilterBadge } from "@/shared/ui/listFilterBadges";

export function BusinessPartnerFilters({
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
  filters: BusinessPartnerFilterState;
  appliedFilters: BusinessPartnerFilterState;
  columnSettings?: ReactNode;
  loading: boolean;
  onChange: <K extends keyof BusinessPartnerFilterState>(
    field: K,
    value: BusinessPartnerFilterState[K],
  ) => void;
  onRemove: (fields: readonly (keyof BusinessPartnerFilterState)[]) => void;
  onReset: () => void;
  onResetFilters: () => void;
  onSearch: () => void;
}) {
  const typeLabels = {
    WHOLESALE: "도매",
    RETAIL: "소매",
    AUCTION_HOUSE: "경매장",
  };
  const appliedBadges = [
    ...valueFilterBadge(
      "keyword",
      `검색: ${appliedFilters.keyword.trim()}`,
      appliedFilters.keyword,
      onRemove,
    ),
    ...valueFilterBadge(
      "partnerType",
      `거래처 유형: ${appliedFilters.partnerType ? typeLabels[appliedFilters.partnerType] : ""}`,
      appliedFilters.partnerType,
      onRemove,
    ),
    ...valueFilterBadge(
      "active",
      `상태: ${appliedFilters.active === "ACTIVE" ? "사용중" : "사용중지"}`,
      appliedFilters.active,
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
      searchPlaceholder="거래처명, 대표자명, 연락처, 메모"
      settingsControl={columnSettings}
      onKeywordChange={(value) => onChange("keyword", value)}
      onClearKeyword={() => onRemove(["keyword"])}
      onReset={onReset}
      onResetFilters={onResetFilters}
      onSearch={onSearch}
    >
      <FilterSelect
        layout="row"
        label="거래처 유형"
        value={filters.partnerType}
        onChange={(value) =>
          onChange(
            "partnerType",
            (["WHOLESALE", "RETAIL", "AUCTION_HOUSE"].find(
              (type) => type === value,
            ) ?? "") as BusinessPartnerFilterState["partnerType"],
          )
        }
      >
        <option value="">전체</option>
        <option value="WHOLESALE">도매</option>
        <option value="RETAIL">소매</option>
        <option value="AUCTION_HOUSE">경매장</option>
      </FilterSelect>
      <FilterSelect
        layout="row"
        label="상태"
        value={filters.active}
        onChange={(value) =>
          onChange(
            "active",
            (["ACTIVE", "INACTIVE"].find((active) => active === value) ??
              "") as BusinessPartnerFilterState["active"],
          )
        }
      >
        <option value="">전체</option>
        <option value="ACTIVE">사용중</option>
        <option value="INACTIVE">사용중지</option>
      </FilterSelect>
    </ListFilterToolbar>
  );
}
