"use client";

import { CalendarDays, RotateCcw, Search } from "lucide-react";
import { isAnalyticsDateRangeValid } from "../../lib/analyticsDateRange";
import type { AnalyticsFilters as FilterValues } from "../../model/types";

export function AnalyticsFilters({
  values,
  onChange,
  onApply,
  onReset,
  pending,
}: {
  values: FilterValues;
  onChange: (key: keyof FilterValues, value: string) => void;
  onApply: () => void;
  onReset: () => void;
  pending: boolean;
}) {
  const invalidRange = !isAnalyticsDateRangeValid(
    values.dateFrom,
    values.dateTo,
  );
  return (
    <section className="shrink-0 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm">
      <div className="flex flex-wrap items-end gap-2">
        <label className="space-y-1 text-xs font-semibold text-[#4d5a51]">
          <span>판매일·작업 예정일 기준 기간</span>
          <div className="flex h-9 min-w-[21rem] items-center gap-2 rounded-md border border-[#d7ded8] px-3">
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
          disabled={invalidRange || pending}
        >
          <Search className="h-4 w-4" />
          {pending ? "조회 중" : "조회"}
        </button>
      </div>
      <p
        className={`mt-2 text-[11px] ${invalidRange ? "text-[#c5442d]" : "text-[#7a857e]"}`}
      >
        {invalidRange
          ? "시작일과 종료일을 확인해 주세요. 기간은 최대 2년입니다."
          : "* 완료된 판매 전표와 완료·정정된 작업만 집계합니다."}
      </p>
    </section>
  );
}
