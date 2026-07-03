import { RotateCcw, Search } from "lucide-react";
import { auctionStatusOptions } from "../../lib/auctionDisplay";
import type { AuctionFilterState } from "../../model/types";

export function AuctionFilters({
  filters,
  loading,
  onChange,
  onSearch,
  onReset,
}: {
  filters: AuctionFilterState;
  loading: boolean;
  onChange: <K extends keyof AuctionFilterState>(
    field: K,
    value: AuctionFilterState[K],
  ) => void;
  onSearch: () => void;
  onReset: () => void;
}) {
  return (
    <section className="rounded-md border border-[#dfe5dc] bg-white p-3 shadow-sm">
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4 2xl:grid-cols-8">
        <FilterInput
          label="출하 시작일"
          type="date"
          value={filters.from}
          onChange={(value) => onChange("from", value)}
        />
        <FilterInput
          label="출하 종료일"
          type="date"
          value={filters.to}
          onChange={(value) => onChange("to", value)}
        />
        <FilterInput
          label="경매장"
          value={filters.market}
          onChange={(value) => onChange("market", value)}
        />
        <FilterInput
          label="품종명"
          value={filters.variety}
          onChange={(value) => onChange("variety", value)}
        />
        <FilterInput
          label="등급"
          value={filters.grade}
          onChange={(value) => onChange("grade", value)}
        />
        <label className="space-y-1 text-xs font-semibold text-[#4d5c52]">
          상태
          <select
            className="h-9 w-full rounded-md border border-[#d9e0d8] bg-white px-2 text-sm"
            value={filters.status}
            onChange={(event) => onChange("status", event.target.value)}
          >
            <option value="">전체</option>
            {auctionStatusOptions.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <FilterInput
          label="키워드"
          value={filters.keyword}
          onChange={(value) => onChange("keyword", value)}
        />
        <div className="flex items-end gap-2">
          <button
            className="inline-flex h-9 flex-1 items-center justify-center gap-1.5 rounded-md border border-[#d9e0d8] bg-white text-sm font-semibold"
            type="button"
            onClick={onReset}
          >
            <RotateCcw className="h-4 w-4" />
            초기화
          </button>
          <button
            className="inline-flex h-9 flex-1 items-center justify-center gap-1.5 rounded-md bg-[#159447] text-sm font-semibold text-white disabled:opacity-60"
            type="button"
            disabled={loading}
            onClick={onSearch}
          >
            <Search className="h-4 w-4" />
            조회
          </button>
        </div>
      </div>
      <div className="mt-3 flex flex-wrap gap-4 border-t border-[#edf0ec] pt-3 text-sm font-semibold text-[#46544a]">
        <FilterCheck
          label="확인 필요만"
          checked={filters.reviewOnly}
          onChange={(checked) => onChange("reviewOnly", checked)}
        />
        <FilterCheck
          label="반환 추정만"
          checked={filters.returnOnly}
          onChange={(checked) => onChange("returnOnly", checked)}
        />
        <FilterCheck
          label="재경매 대기만"
          checked={filters.waitingOnly}
          onChange={(checked) => onChange("waitingOnly", checked)}
        />
      </div>
    </section>
  );
}

function FilterInput({
  label,
  value,
  type = "text",
  onChange,
}: {
  label: string;
  value: string;
  type?: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="space-y-1 text-xs font-semibold text-[#4d5c52]">
      {label}
      <input
        className="h-9 w-full rounded-md border border-[#d9e0d8] px-2 text-sm"
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function FilterCheck({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="inline-flex items-center gap-2">
      <input
        className="h-4 w-4 accent-[#159447]"
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      {label}
    </label>
  );
}
