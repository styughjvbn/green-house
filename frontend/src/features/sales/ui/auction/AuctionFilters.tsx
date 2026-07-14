import type { AuctionTrackingSummary } from "@/entities/farm/types";
import { auctionStatusOptions } from "../../lib/auctionDisplay";
import type { AuctionFilterState } from "../../model/types";
import {
  FilterCheck,
  FilterDateRange,
  FilterGrid,
  FilterInput,
  FilterPanel,
  FilterResetButton,
  FilterSearchButton,
  FilterSelect,
} from "@/shared/ui/FilterControls";

export function AuctionFilters({
  filters,
  loading,
  summary,
  onChange,
  onSearch,
  onReset,
}: {
  filters: AuctionFilterState;
  loading: boolean;
  summary: AuctionTrackingSummary;
  onChange: <K extends keyof AuctionFilterState>(
    field: K,
    value: AuctionFilterState[K],
  ) => void;
  onSearch: () => void;
  onReset: () => void;
}) {
  return (
    <FilterPanel
      footer={
        <>
          <FilterCheck
            label={`확인 필요만 ${summary.reviewRequiredCount.toLocaleString()}건`}
            checked={filters.reviewOnly}
            onChange={(checked) => onChange("reviewOnly", checked)}
          />
          <FilterCheck
            label={`재경매 대기만 ${summary.waitingQuantity.toLocaleString()}분`}
            checked={filters.waitingOnly}
            onChange={(checked) => onChange("waitingOnly", checked)}
          />
        </>
      }
    >
      <FilterGrid className="sm:grid-cols-2 lg:grid-cols-[2fr_0.5fr_0.5fr_0.5fr_1fr_1.4fr_auto_auto]">
        <FilterDateRange
          from={filters.from}
          to={filters.to}
          onFromChange={(value) => onChange("from", value)}
          onToChange={(value) => onChange("to", value)}
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
        <FilterSelect
          label="상태"
          value={filters.status}
          onChange={(value) => onChange("status", value)}
        >
          <option value="">전체</option>
          {auctionStatusOptions.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </FilterSelect>
        <FilterInput
          label="키워드"
          value={filters.keyword}
          onChange={(value) => onChange("keyword", value)}
        />
        <FilterResetButton onClick={onReset} />
        <FilterSearchButton
          disabled={loading}
          label="조회"
          onClick={onSearch}
        />
      </FilterGrid>
    </FilterPanel>
  );
}
