"use client";

import type {
  AuctionLotPage,
  AuctionTrackingSummary,
} from "@/entities/farm/types";
import { usePagedListUrlActions } from "@/shared/api/usePagedListUrlActions";
import { TabError, TabLayout, TabSplit, TabStack } from "@/shared/ui/TabLayout";
import {
  AUCTION_FILTER_KEYS,
  writeAuctionFilterParams,
} from "../lib/salesUrlFilters";
import { useAuctionTracking } from "../model/useAuctionTracking";
import type { AuctionFilterState } from "../model/types";
import { AuctionFilters } from "./auction/AuctionFilters";
import { AuctionLotDetail } from "./auction/AuctionLotDetail";
import { AuctionLotList } from "./auction/AuctionLotList";

export function SalesAuctionPage({
  initialPage,
  initialSummary,
  initialFilters,
}: {
  initialPage: AuctionLotPage;
  initialSummary: AuctionTrackingSummary;
  initialFilters: AuctionFilterState;
}) {
  const tracking = useAuctionTracking({
    initialFilters,
    initialPage,
    initialSummary,
  });

  const listActions = usePagedListUrlActions({
    filters: tracking.filters,
    filterKeys: AUCTION_FILTER_KEYS,
    writeFilterParams: writeAuctionFilterParams,
    onSearch: tracking.search,
    onReset: tracking.resetFilters,
    onPageChange: tracking.setPage,
    onPageSizeChange: tracking.setPageSize,
  });

  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <TabStack>
          <AuctionFilters
            filters={tracking.filters}
            loading={tracking.loading}
            summary={tracking.summary}
            onChange={tracking.updateFilter}
            onSearch={listActions.search}
            onReset={listActions.reset}
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
              onPageChange={listActions.changePage}
              onPageSizeChange={listActions.changePageSize}
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
      </TabLayout>
    </main>
  );
}
