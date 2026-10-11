"use client";

import { BusinessPartnerSelect } from "../common/BusinessPartnerSelect";
import type { SalesFilterState } from "../../model/types";
import {
  FilterDateRange,
  FilterGrid,
  FilterInput,
  FilterPanel,
  FilterResetButton,
  FilterSearchButton,
  FilterSelect,
} from "@/shared/ui/FilterControls";

export function SalesFilters({
  filters,
  onChange,
  onReset,
  onSearch,
  onSalesTypeChange,
}: {
  filters: SalesFilterState;
  onChange: <K extends keyof SalesFilterState>(
    field: K,
    value: SalesFilterState[K],
  ) => void;
  onSalesTypeChange: (value: SalesFilterState["salesType"]) => void;
  onReset: () => void;
  onSearch: () => void;
}) {
  return (
    <FilterPanel>
      <FilterGrid className="lg:grid-cols-[2.3fr_1fr_1.5fr_0.7fr_1.5fr_auto_auto]">
        <FilterDateRange
          from={filters.from}
          to={filters.to}
          onFromChange={(value) => onChange("from", value)}
          onToChange={(value) => onChange("to", value)}
        />

        <FilterSelect
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
          key={filters.salesType}
          auctionHouse={
            filters.salesType === ""
              ? undefined
              : filters.salesType === "AUCTION"
          }
          label="거래처"
          value={filters.partnerId}
          onChange={(value) => onChange("partnerId", value)}
        />

        <FilterSelect
          label="출고·출하"
          value={filters.salesStatus}
          onChange={(value) => onChange("salesStatus", value)}
        >
          <option value="">전체</option>
          <option value="작성중">대기</option>
          <option value="완료">완료</option>
          <option value="취소">취소</option>
        </FilterSelect>

        {/* TODO: 수납 비활성화 — 전표 등록 마무리 후 연결 예정. */}

        <FilterInput
          label="키워드"
          placeholder="전표번호, 거래처명, 메모"
          value={filters.keyword}
          onChange={(value) => onChange("keyword", value)}
        />

        <FilterResetButton onClick={onReset} />
        <FilterSearchButton type="button" onClick={onSearch} />
      </FilterGrid>
    </FilterPanel>
  );
}
