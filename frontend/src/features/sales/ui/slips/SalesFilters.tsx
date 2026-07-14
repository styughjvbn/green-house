"use client";

import type { BusinessPartner } from "@/entities/farm/types";
import type { SalesFilterState } from "../../model/types";
import {
  FilterDateRange,
  FilterGrid,
  FilterInput,
  FilterPanel,
  FilterResetButton,
  FilterSelect,
} from "@/shared/ui/FilterControls";

export function SalesFilters({
  partners,
  filters,
  onChange,
  onReset,
}: {
  partners: BusinessPartner[];
  filters: SalesFilterState;
  onChange: <K extends keyof SalesFilterState>(
    field: K,
    value: SalesFilterState[K],
  ) => void;
  onReset: () => void;
}) {
  return (
    <FilterPanel>
      <FilterGrid className="lg:grid-cols-[2fr_1.3fr_1.1fr_1.1fr_1.7fr_auto]">
        <FilterDateRange
          from={filters.from}
          to={filters.to}
          onFromChange={(value) => onChange("from", value)}
          onToChange={(value) => onChange("to", value)}
        />

        <FilterSelect
          label="거래처"
          value={filters.partnerId}
          onChange={(value) => onChange("partnerId", value)}
        >
          <option value="">전체 거래처</option>
          {partners.map((partner) => (
            <option key={partner.id} value={partner.id}>
              {partner.name}
            </option>
          ))}
        </FilterSelect>

        <FilterSelect
          label="판매 상태"
          value={filters.salesStatus}
          onChange={(value) => onChange("salesStatus", value)}
        >
          <option value="">전체</option>
          <option value="작성중">작성중</option>
          <option value="출하 완료">출하 완료</option>
          <option value="출고 완료">출고 완료</option>
          <option value="취소">취소</option>
        </FilterSelect>

        <FilterSelect
          label="입금 상태"
          value={filters.paymentStatus}
          onChange={(value) => onChange("paymentStatus", value)}
        >
          <option value="">전체</option>
          <option value="미입금">미입금</option>
          <option value="입금 완료">입금 완료</option>
        </FilterSelect>

        <FilterInput
          label="키워드"
          placeholder="전표번호, 거래처명, 메모"
          value={filters.keyword}
          onChange={(value) => onChange("keyword", value)}
        />

        <FilterResetButton onClick={onReset} />
      </FilterGrid>
    </FilterPanel>
  );
}
