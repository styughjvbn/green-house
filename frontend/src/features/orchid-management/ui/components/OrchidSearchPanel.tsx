"use client";

import { ChevronDown, ChevronUp, Search } from "lucide-react";
import { useMemo, useState } from "react";
import type { OrchidGroup } from "@/entities/farm/types";
import type { OrchidManagementSearchState } from "../../model/types";

export default function OrchidSearchPanel({
  currentHouseId,
  currentSelectedOrchidGroupId,
  filteredCount,
  filters,
  hasActiveSearch,
  loading,
  results,
  onClear,
  onSelectResult,
  onUpdateFilter,
}: {
  currentHouseId: number;
  currentSelectedOrchidGroupId: number | null;
  filteredCount: number;
  filters: OrchidManagementSearchState;
  hasActiveSearch: boolean;
  loading: boolean;
  results: OrchidGroup[];
  onClear: () => void;
  onSelectResult: (orchidGroup: OrchidGroup) => void;
  onUpdateFilter: <K extends keyof OrchidManagementSearchState>(
    field: K,
    value: OrchidManagementSearchState[K],
  ) => void;
}) {
  const [open, setOpen] = useState(true);
  const statuses = useMemo(
    () =>
      Array.from(
        new Set([
          ...results.map((orchidGroup) => orchidGroup.status),
          ...(filters.status ? [filters.status] : []),
        ]),
      ).sort(),
    [filters.status, results],
  );

  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-3 shadow-sm">
      <div className="grid gap-2 xl:grid-cols-[minmax(0,1fr)_180px_auto]">
        <label className="relative block">
          <Search
            className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-[#7a847c]"
            strokeWidth={1.8}
            aria-hidden="true"
          />
          <input
            className="h-10 w-full rounded-md border border-[#dfe5dc] bg-white pr-3 pl-9 text-sm text-[#17251b] outline-none placeholder:text-[#97a098]"
            placeholder="전체 농장 난 묶음 검색"
            type="text"
            value={filters.keyword}
            onChange={(event) => onUpdateFilter("keyword", event.target.value)}
          />
        </label>
        <select
          className="h-10 rounded-md border border-[#dfe5dc] bg-white px-3 text-sm text-[#17251b] outline-none"
          value={filters.status}
          onChange={(event) => onUpdateFilter("status", event.target.value)}
        >
          <option value="">전체 상태</option>
          {statuses.map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </select>
        <button
          className={`h-10 rounded-md border px-3 text-sm font-semibold ${
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

      <div className="mt-3 rounded-md border border-[#e3e8e1] bg-[#fafcf9]">
        <button
          className="flex w-full items-center justify-between px-3 py-2 text-left"
          onClick={() => setOpen((current) => !current)}
          type="button"
        >
          <div>
            <p className="text-sm font-semibold text-[#17251b]">
              검색 결과 {hasActiveSearch ? filteredCount : 0}개
            </p>
            <p className="mt-0.5 text-xs text-[#6d786f]">
              결과 클릭 시 해당 동과 난 묶음으로 이동
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
            ) : !hasActiveSearch ? (
              <p className="px-3 py-3 text-sm text-[#5d6860]">
                키워드 또는 상태를 입력하세요.
              </p>
            ) : results.length === 0 ? (
              <p className="px-3 py-3 text-sm text-[#5d6860]">
                검색 결과가 없습니다.
              </p>
            ) : (
              <div className="max-h-[320px] overflow-y-auto">
                {results.map((orchidGroup) => {
                  const selected =
                    orchidGroup.id === currentSelectedOrchidGroupId &&
                    orchidGroup.houseId === currentHouseId;

                  return (
                    <button
                      key={orchidGroup.id}
                      className={`block w-full border-b border-[#eef1ec] px-3 py-2 text-left last:border-b-0 ${
                        selected
                          ? "bg-[#eef5ff]"
                          : orchidGroup.houseId === currentHouseId
                            ? "bg-[#f8fcf7] hover:bg-[#f2f8f0]"
                            : "bg-white hover:bg-[#f7faf6]"
                      }`}
                      onClick={() => onSelectResult(orchidGroup)}
                      type="button"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-semibold text-[#17251b]">
                            {orchidGroup.varietyName}
                          </p>
                          <p className="mt-1 text-xs text-[#5e6a61]">
                            {orchidGroup.houseNumber}동{" "}
                            {orchidGroup.physicalBedNumber}
                            배드 {orchidGroup.bedZoneName}
                          </p>
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
                })}
              </div>
            )}
          </div>
        ) : null}
      </div>
    </section>
  );
}
