"use client";

import type { BusinessPartnerFilterState } from "../../model/types";
import {
  FilterGrid,
  FilterInput,
  FilterPanel,
  FilterResetButton,
  FilterSearchButton,
  FilterSelect,
} from "@/shared/ui/FilterControls";

export function BusinessPartnerFilters({
  filters,
  onChange,
  onReset,
}: {
  filters: BusinessPartnerFilterState;
  onChange: <K extends keyof BusinessPartnerFilterState>(
    field: K,
    value: BusinessPartnerFilterState[K],
  ) => void;
  onReset: () => void;
}) {
  return (
    <FilterPanel>
      <FilterGrid className="md:grid-cols-2 lg:grid-cols-[1fr_1fr_2.2fr_auto_auto]">
        <FilterSelect
          label="거래처 유형"
          value={filters.partnerType}
          onChange={(value) => onChange("partnerType", value)}
        >
          <option value="">전체</option>
          <option value="WHOLESALE">도매</option>
          <option value="RETAIL">소매</option>
          <option value="AUCTION_HOUSE">경매장</option>
        </FilterSelect>

        <FilterSelect
          label="상태"
          value={filters.active}
          onChange={(value) => onChange("active", value)}
        >
          <option value="">전체</option>
          <option value="ACTIVE">사용중</option>
          <option value="INACTIVE">사용중지</option>
        </FilterSelect>

        <FilterInput
          label="키워드"
          placeholder="거래처명, 대표자명, 연락처, 메모"
          value={filters.keyword}
          onChange={(value) => onChange("keyword", value)}
        />

        <FilterResetButton onClick={onReset} />
        <FilterSearchButton />
      </FilterGrid>
    </FilterPanel>
  );
}
