"use client";

import type { ReactNode } from "react";
import { ChevronDown, RefreshCw, Search } from "lucide-react";
import type { WorkRecordTargetType } from "@/entities/farm/types";
import type { WorkRecordFilterState } from "../../model/types";

export function WorkRecordFilters({
  filters,
  workTypes,
  onChange,
  onReset,
}: {
  filters: WorkRecordFilterState;
  workTypes: string[];
  onChange: <K extends keyof WorkRecordFilterState>(
    field: K,
    value: WorkRecordFilterState[K],
  ) => void;
  onReset: () => void;
}) {
  const selectedTargetLabel =
    targetOptions.find((option) => option.value === filters.targetType)?.label ??
    "전체 농장";

  return (
    <section className="rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="grid gap-3 xl:grid-cols-[1.1fr_2fr_1.1fr_1.4fr_auto_auto]">
        <FilterSelect
          label="작업 유형"
          value={filters.workType}
          onChange={(value) => onChange("workType", value)}
        >
          <option value="">전체</option>
          {workTypes.map((workType) => (
            <option key={workType} value={workType}>
              {workType}
            </option>
          ))}
        </FilterSelect>

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

          <label>
            <span className="mb-2 block text-sm font-semibold text-[#344138]">
              작업자
            </span>
            <input
              className="h-10 w-full rounded-md border border-[#cfd8cc] px-3 text-sm"
              placeholder="전체"
              value={filters.worker}
              onChange={(event) => onChange("worker", event.target.value)}
            />
          </label>

          <label>
            <span className="mb-2 block text-sm font-semibold text-[#344138]">
              키워드
            </span>
            <input
              className="h-10 w-full rounded-md border border-[#cfd8cc] px-3 text-sm"
              placeholder="약제명, 메모 검색"
              value={filters.keyword}
              onChange={(event) => onChange("keyword", event.target.value)}
            />
          </label>

          <button
            className="mt-7 inline-flex h-10 items-center justify-center gap-2 rounded-md bg-[#159447] px-6 text-sm font-semibold text-white"
            type="button"
          >
            <Search className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
            검색
          </button>
          <button
            className="mt-7 inline-flex h-10 items-center justify-center gap-2 rounded-md border border-[#dfe5dc] bg-white px-5 text-sm font-semibold text-[#344138]"
            type="button"
            onClick={onReset}
          >
            <RefreshCw className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
            초기화
          </button>
        </div>

        <details className="group mt-3 rounded-md border border-[#e6ebe3] bg-[#fbfcfa]">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-3 py-2 text-sm font-bold text-[#344138]">
            <span>
              대상 선택
              <span className="ml-2 rounded-full bg-[#eef7ec] px-2 py-0.5 text-xs text-[#159447]">
                {selectedTargetLabel}
              </span>
            </span>
            <ChevronDown className="h-4 w-4 transition group-open:rotate-180" strokeWidth={1.8} aria-hidden="true" />
          </summary>
          <div className="border-t border-[#e6ebe3] px-3 py-3">
            <div className="grid gap-2 md:grid-cols-5">
              {targetOptions.map((option) => (
                <label
                  key={option.value}
                  className="flex h-9 items-center gap-2 rounded-md border border-[#dfe5dc] bg-white px-3 text-sm font-semibold text-[#344138]"
                >
                  <input
                    checked={filters.targetType === option.value}
                    className="accent-[#159447]"
                    name="targetTypeFilter"
                    type="radio"
                    onChange={() => onChange("targetType", option.value)}
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </div>
        </details>
    </section>
  );
}

const targetOptions: Array<{
  label: string;
  value: "" | WorkRecordTargetType;
}> = [
  { label: "전체 농장", value: "" },
  { label: "동", value: "HOUSE" },
  { label: "물리 배드", value: "PHYSICAL_BED" },
  { label: "논리 구역", value: "BED_ZONE" },
  { label: "난 묶음", value: "ORCHID_GROUP" },
];

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
