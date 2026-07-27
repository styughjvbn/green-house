import type {
  AuctionLotPage,
  AuctionTrackingSummary,
} from "@/entities/farm/types";
import type { AuctionFilterState } from "../../model/types";
import { useAuctionTracking } from "../../model/useAuctionTracking";
import { AuctionFilters } from "./AuctionFilters";
import { AuctionLotDetail } from "./AuctionLotDetail";
import { AuctionLotList } from "./AuctionLotList";
import { TabError, TabSplit, TabStack } from "@/shared/ui/TabLayout";

export function AuctionTrackingView({
  initialPage,
  initialSummary,
  initialFilters,
  filters,
  queryFilters,
  queryPage,
  querySize,
  onSearch,
  onFilterChange,
  onResetFilters,
  onPageChange,
  onPageSizeChange,
}: {
  initialPage: AuctionLotPage;
  initialSummary: AuctionTrackingSummary;
  initialFilters: AuctionFilterState;
  filters: AuctionFilterState;
  queryFilters: AuctionFilterState;
  queryPage: number;
  querySize: number;
  onSearch: (filters: AuctionFilterState) => void;
  onFilterChange: <K extends keyof AuctionFilterState>(
    field: K,
    value: AuctionFilterState[K],
  ) => void;
  onResetFilters: () => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}) {
  const tracking = useAuctionTracking({
    initialPage,
    initialSummary,
    initialFilters,
    queryFilters,
    queryPage,
    querySize,
  });

  return (
    <TabStack>
      <AuctionFilters
        filters={filters}
        loading={tracking.loading}
        summary={tracking.summary}
        onChange={onFilterChange}
        onSearch={() => onSearch(filters)}
        onReset={onResetFilters}
      />
      <TabError message={tracking.error} />
      <TabSplit
        columns="lg:grid-cols-[minmax(0,1.15fr)_minmax(420px,0.85fr)]"
        gap="gap-3"
      >
        <AuctionLotList
          lots={tracking.lots}
          loading={tracking.listLoading}
          page={tracking.page}
          pageSize={tracking.pageSize}
          totalElements={tracking.totalElements}
          totalPages={tracking.totalPages}
          selectedId={tracking.selectedLot?.id ?? null}
          onSelect={tracking.setSelectedId}
          onPageChange={onPageChange}
          onPageSizeChange={onPageSizeChange}
        />
        <AuctionLotDetail
          key={tracking.selectedLot?.id ?? "empty"}
          lot={tracking.selectedLot}
          loading={tracking.loading}
          onAddResult={tracking.addResult}
          onConfirmReturn={tracking.confirmReturn}
          onAdjust={tracking.adjustQuantity}
        />
      </TabSplit>
    </TabStack>
  );
}
