"use client";

import { ChevronDown, ChevronUp, Search } from "lucide-react";
import { type FocusEvent, useMemo, useState } from "react";
import type { OrchidGroup } from "@/entities/farm/types";

export type OrchidGroupSearchFilters = {
  keyword: string;
  status: string;
};

export function OrchidGroupSearchPanel<
  TFilters extends OrchidGroupSearchFilters,
>({
  currentHouseId,
  currentSelectedOrchidGroupId,
  filteredCount,
  filters,
  hasActiveSearch,
  loading,
  placeholder,
  results,
  resultDescription,
  showResultIndex = false,
  statuses: initialStatuses = [],
  variant = "default",
  onClear,
  onSelectResult,
  onUpdateFilter,
}: {
  currentHouseId?: number;
  currentSelectedOrchidGroupId: number | null;
  filteredCount?: number;
  filters: TFilters;
  hasActiveSearch: boolean;
  loading: boolean;
  placeholder: string;
  results: OrchidGroup[];
  resultDescription: string;
  showResultIndex?: boolean;
  statuses?: string[];
  variant?: "default" | "soft";
  onClear: () => void;
  onSelectResult: (orchidGroup: OrchidGroup) => void;
  onUpdateFilter: <K extends keyof TFilters>(
    field: K,
    value: TFilters[K],
  ) => void;
}) {
  const [collapsed, setCollapsed] = useState(false);
  const statuses = useMemo(
    () =>
      Array.from(
        new Set([
          ...initialStatuses,
          ...(filters.status ? [filters.status] : []),
          ...results.map((orchidGroup) => orchidGroup.status),
        ]),
      ).sort(),
    [filters.status, initialStatuses, results],
  );
  const resultCount = hasActiveSearch ? (filteredCount ?? results.length) : 0;
  const open = hasActiveSearch && !collapsed;

  function handleBlur(event: FocusEvent<HTMLElement>) {
    if (event.currentTarget.contains(event.relatedTarget)) {
      return;
    }
    if (hasActiveSearch && !loading && resultCount === 0) {
      onClear();
    }
  }

  return (
    <section
      className={`border bg-white p-1.5 shadow-sm ${
        variant === "soft"
          ? "rounded-xl border-[#d9e2d5]"
          : "rounded-md border-[#d7ddd4]"
      }`}
      onBlur={handleBlur}
    >
      <div className="grid gap-2 lg:grid-cols-[minmax(0,1fr)_70px]">
        <label className="relative block">
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-[#77857c]"
            strokeWidth={1.8}
          />
          <input
            className="h-7 w-full rounded-md border border-[#dfe5dc] bg-white pr-3 pl-9 text-sm text-[#17251b] outline-none placeholder:text-[#98a29a]"
            placeholder={placeholder}
            type="text"
            value={filters.keyword}
            onChange={(event) =>
              onUpdateFilter(
                "keyword",
                event.target.value as TFilters["keyword"],
              )
            }
          />
        </label>
        {/* <select
          className="h-7 rounded-md border border-[#dfe5dc] bg-white px-3 text-sm text-[#17251b] outline-none"
          value={filters.status}
          onChange={(event) =>
            onUpdateFilter("status", event.target.value as TFilters["status"])
          }
        >
          <option value="">전체 상태</option>
          {statuses.map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </select> */}
        <button
          className={`h-7 rounded-md border px-3 text-sm font-semibold ${
            hasActiveSearch
              ? "border-[#dfe5dc] bg-white text-[#344138]"
              : "cursor-default border-[#edf0ec] bg-[#f7f8f6] text-[#a0a8a0]"
          }`}
          disabled={!hasActiveSearch}
          onClick={onClear}
          type="button"
        >
          초기화
        </button>
      </div>

      {hasActiveSearch ? (
        <div className="mt-2 rounded-md border border-[#e3e8e1] bg-[#fafcf9]">
          <button
            className="flex w-full items-center justify-between px-3 py-2 text-left"
            onClick={() => setCollapsed((current) => !current)}
            type="button"
          >
            <div>
              <p className="text-sm font-semibold text-[#17251b]">
                검색 결과 {resultCount}개
              </p>
              <p className="mt-0.5 text-xs text-[#6d786f]">
                {resultDescription}
              </p>
            </div>
            {open ? (
              <ChevronUp className="h-4 w-4 text-[#5e6a61]" />
            ) : (
              <ChevronDown className="h-4 w-4 text-[#5e6a61]" />
            )}
          </button>

          {open ? (
            <div className="border-t border-[#e3e8e1] bg-white">
              {loading ? (
                <p className="px-3 py-3 text-sm text-[#5d6860]">검색 중</p>
              ) : (
                <div className="max-h-[150px] overflow-y-auto">
                  {results.map((orchidGroup, index) => (
                    <SearchResultButton
                      key={orchidGroup.id}
                      currentHouseId={currentHouseId}
                      currentSelectedOrchidGroupId={
                        currentSelectedOrchidGroupId
                      }
                      indexLabel={showResultIndex ? `#${index + 1}` : null}
                      orchidGroup={orchidGroup}
                      onSelectResult={onSelectResult}
                    />
                  ))}
                </div>
              )}
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function SearchResultButton({
  currentHouseId,
  currentSelectedOrchidGroupId,
  indexLabel,
  orchidGroup,
  onSelectResult,
}: {
  currentHouseId?: number;
  currentSelectedOrchidGroupId: number | null;
  indexLabel: string | null;
  orchidGroup: OrchidGroup;
  onSelectResult: (orchidGroup: OrchidGroup) => void;
}) {
  const selected =
    orchidGroup.id === currentSelectedOrchidGroupId &&
    (currentHouseId == null || orchidGroup.houseId === currentHouseId);
  const sameHouse =
    currentHouseId != null && orchidGroup.houseId === currentHouseId;

  return (
    <button
      className={`block w-full border-b border-[#eef1ec] px-3 py-2 text-left last:border-b-0 ${
        selected
          ? "bg-[#eef5ff]"
          : sameHouse
            ? "bg-[#f8fcf7] hover:bg-[#f2f8f0]"
            : "bg-white hover:bg-[#f7faf6]"
      }`}
      onClick={() => onSelectResult(orchidGroup)}
      type="button"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-2">
          {indexLabel ? (
            <span className="mt-0.5 w-6 shrink-0 text-right text-[11px] font-semibold text-[#9aa49e]">
              {indexLabel}
            </span>
          ) : null}
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-[#17251b]">
              {orchidGroup.varietyName}
            </p>
            <p className="mt-1 text-xs text-[#5e6a61]">
              {orchidGroup.houseNumber}동 {orchidGroup.physicalBedNumber}배드{" "}
              {orchidGroup.bedZoneName}
            </p>
          </div>
        </div>
        <div className="shrink-0 text-right">
          <p className="text-xs font-semibold text-[#2d3a31]">
            {orchidGroup.quantity}분
          </p>
          <p className="mt-1 text-[11px] text-[#728076]">
            {orchidGroup.status}
          </p>
        </div>
      </div>
    </button>
  );
}
