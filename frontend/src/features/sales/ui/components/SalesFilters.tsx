"use client";

import type { ReactNode } from "react";
import { RefreshCw, Search } from "lucide-react";
import type { BusinessPartner } from "@/entities/farm/types";
import type { SalesFilterState } from "../../model/types";

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
    <section className="rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="grid gap-3 md:grid-cols-2 2xl:grid-cols-[2fr_1.3fr_1.1fr_1.1fr_1.7fr_auto_auto]">
        <div>
          <p className="mb-2 text-sm font-semibold text-[#344138]">기간</p>
          <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-2">
            <input
              className="h-10 rounded-md border border-[#cfd8cc] px-3 text-sm"
              type="date"
              value={filters.from}
              onChange={(event) => onChange("from", event.target.value)}
            />
            <span className="text-[#7a8680]">~</span>
            <input
              className="h-10 rounded-md border border-[#cfd8cc] px-3 text-sm"
              type="date"
              value={filters.to}
              onChange={(event) => onChange("to", event.target.value)}
            />
          </div>
        </div>

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
          <option value="판매 확정">판매 확정</option>
          <option value="출고 완료">출고 완료</option>
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

        <label>
          <span className="mb-2 block text-sm font-semibold text-[#344138]">
            키워드
          </span>
          <input
            className="h-10 w-full rounded-md border border-[#cfd8cc] px-3 text-sm"
            placeholder="전표번호, 거래처명, 메모"
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
