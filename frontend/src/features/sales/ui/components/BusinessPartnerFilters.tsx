"use client";

import type { ReactNode } from "react";
import { RefreshCw, Search } from "lucide-react";
import type { BusinessPartnerFilterState } from "../../model/types";

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
    <section className="rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="grid gap-3 md:grid-cols-2 2xl:grid-cols-[1fr_1fr_2.2fr_auto_auto]">
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

        <label>
          <span className="mb-2 block text-sm font-semibold text-[#344138]">
            키워드
          </span>
          <input
            className="h-10 w-full rounded-md border border-[#cfd8cc] px-3 text-sm"
            placeholder="거래처명, 대표자명, 연락처, 메모"
            value={filters.keyword}
            onChange={(event) => onChange("keyword", event.target.value)}
          />
        </label>

        <button
          className="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-[#dfe5dc] bg-white px-4 text-sm font-semibold text-[#344138] 2xl:mt-7"
          type="button"
          onClick={onReset}
        >
          <RefreshCw className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          초기화
        </button>
        <button
          className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-[#159447] px-5 text-sm font-semibold text-white 2xl:mt-7"
          type="button"
        >
          <Search className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          검색
        </button>
      </div>
    </section>
  );
}

function FilterSelect({
  children,
  label,
  onChange,
  value,
}: {
  children: ReactNode;
  label: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <label>
      <span className="mb-2 block text-sm font-semibold text-[#344138]">
        {label}
      </span>
      <select
        className="h-10 w-full rounded-md border border-[#cfd8cc] bg-white px-3 text-sm"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {children}
      </select>
    </label>
  );
}
