import { auctionStatusOptions } from "../../lib/auctionDisplay";
import type { AuctionFilterState } from "../../model/types";
import {
  SalesFilterCheck,
  SalesFilterGrid,
  SalesFilterInput,
  SalesFilterPanel,
  SalesFilterResetButton,
  SalesFilterSearchButton,
  SalesFilterSelect,
} from "../common/SalesFilterControls";

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
    <SalesFilterPanel
      footer={
        <>
          <SalesFilterCheck
            label="확인 필요만"
            checked={filters.reviewOnly}
            onChange={(checked) => onChange("reviewOnly", checked)}
          />
          <SalesFilterCheck
            label="반환 추정만"
            checked={filters.returnOnly}
            onChange={(checked) => onChange("returnOnly", checked)}
          />
          <SalesFilterCheck
            label="재경매 대기만"
            checked={filters.waitingOnly}
            onChange={(checked) => onChange("waitingOnly", checked)}
          />
        </>
      }
    >
      <SalesFilterGrid className="sm:grid-cols-2 lg:grid-cols-4 2xl:grid-cols-[1fr_1fr_1fr_1fr_1fr_1fr_1.4fr_auto_auto]">
        <SalesFilterInput
          label="출하 시작일"
          type="date"
          value={filters.from}
          onChange={(value) => onChange("from", value)}
        />
        <SalesFilterInput
          label="출하 종료일"
          type="date"
          value={filters.to}
          onChange={(value) => onChange("to", value)}
        />
        <SalesFilterInput
          label="경매장"
          value={filters.market}
          onChange={(value) => onChange("market", value)}
        />
        <SalesFilterInput
          label="품종명"
          value={filters.variety}
          onChange={(value) => onChange("variety", value)}
        />
        <SalesFilterInput
          label="등급"
          value={filters.grade}
          onChange={(value) => onChange("grade", value)}
        />
        <SalesFilterSelect
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
        </SalesFilterSelect>
        <SalesFilterInput
          label="키워드"
          value={filters.keyword}
          onChange={(value) => onChange("keyword", value)}
        />
        <SalesFilterResetButton className="2xl:mt-7" onClick={onReset} />
        <SalesFilterSearchButton
          className="2xl:mt-7"
          disabled={loading}
          label="조회"
          onClick={onSearch}
        />
      </SalesFilterGrid>
    </SalesFilterPanel>
  );
}
