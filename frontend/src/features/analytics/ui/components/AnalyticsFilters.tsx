"use client";

import { CalendarDays, RotateCcw, Search } from "lucide-react";
import type { AnalyticsFilters as FilterValues } from "../../model/types";

const controlClass =
  "h-9 min-w-0 rounded-md border border-[#d7ded8] bg-white px-3 text-xs text-[#344038] outline-none focus:border-[#159447]";

export function AnalyticsFilters({
  values,
  varieties,
  partners,
  onChange,
  onApply,
  onReset,
}: {
  values: FilterValues;
  varieties: string[];
  partners: string[];
  onChange: (key: keyof FilterValues, value: string) => void;
  onApply: () => void;
  onReset: () => void;
}) {
  return (
    <section className="rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm">
      <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-[1.55fr_repeat(6,minmax(5.5rem,1fr))_auto_auto] xl:items-end">
        <label className="space-y-1 text-xs font-semibold text-[#4d5a51]">
          <span>기간</span>
          <div className="flex h-9 items-center gap-2 rounded-md border border-[#d7ded8] px-3">
            <CalendarDays className="h-4 w-4 shrink-0" />
            <input
              aria-label="시작일"
              className="min-w-0 flex-1 text-xs outline-none"
              type="date"
              value={values.dateFrom}
              onChange={(event) => onChange("dateFrom", event.target.value)}
            />
            <span>~</span>
            <input
              aria-label="종료일"
              className="min-w-0 flex-1 text-xs outline-none"
              type="date"
              value={values.dateTo}
              onChange={(event) => onChange("dateTo", event.target.value)}
            />
          </div>
        </label>
        <FilterSelect
          label="동"
          value={values.house}
          options={[
            "전체",
            ...Array.from({ length: 15 }, (_, index) => `${index + 1}동`),
          ]}
          onChange={(value) => onChange("house", value)}
        />
        <FilterSelect
          label="물리 배드"
          value={values.bed}
          options={["전체", "1배드", "2배드", "3배드"]}
          onChange={(value) => onChange("bed", value)}
        />
        <FilterSelect
          label="논리 구역"
          value={values.zone}
          options={["전체", "좌측", "우측"]}
          onChange={(value) => onChange("zone", value)}
        />
        <FilterSelect
          label="품종"
          value={values.variety}
          options={["전체", ...varieties]}
          onChange={(value) => onChange("variety", value)}
        />
        <FilterSelect
          label="거래처"
          value={values.partner}
          options={["전체", ...partners]}
          onChange={(value) => onChange("partner", value)}
        />
        <FilterSelect
          label="상태"
          value="전체"
          options={["전체", "정상", "주의", "이상"]}
          onChange={() => undefined}
        />
        <button
          className="flex h-9 items-center justify-center gap-2 rounded-md border border-[#d7ded8] px-4 text-xs font-semibold"
          type="button"
          onClick={onReset}
        >
          <RotateCcw className="h-4 w-4" />
          초기화
        </button>
        <button
          className="flex h-9 items-center justify-center gap-2 rounded-md bg-[#159447] px-5 text-xs font-semibold text-white"
          type="button"
          onClick={onApply}
        >
          <Search className="h-4 w-4" />
          조회
        </button>
      </div>
      <p className="mt-2 text-[11px] text-[#7a857e]">
        * 기간은 최대 2년까지 선택 가능합니다.
      </p>
    </section>
  );
}

function FilterSelect({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="space-y-1 text-xs font-semibold text-[#4d5a51]">
      <span>{label}</span>
      <select
        className={`${controlClass} w-full`}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option}>{option}</option>
        ))}
      </select>
    </label>
  );
}
