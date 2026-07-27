"use client";

import type {
  AuctionLotPage,
  AuctionTrackingSummary,
} from "@/entities/farm/types";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { TabLayout } from "@/shared/ui/TabLayout";
import {
  AUCTION_FILTER_KEYS,
  createInitialAuctionFilters,
  deleteParams,
  writeAuctionFilterParams,
} from "../lib/salesUrlFilters";
import { usePagedListQueryState } from "../model/usePagedListQueryState";
import type { AuctionFilterState } from "../model/types";
import { AuctionTrackingView } from "./auction/AuctionTrackingView";

export function SalesAuctionPage({
  initialPage,
  initialSummary,
  initialFilters,
}: {
  initialPage: AuctionLotPage;
  initialSummary: AuctionTrackingSummary;
  initialFilters: AuctionFilterState;
}) {
  const writeUrlParams = useUrlSearchParamsWriter();
  const auctionQuery = usePagedListQueryState({
    createEmptyFilters: createInitialAuctionFilters,
    initialFilters,
    initialPage: initialPage.page,
    initialSize: initialPage.size,
  });

  const applyFilters = (filters: AuctionFilterState) => {
    auctionQuery.search(filters);
    writeUrlParams((params) => {
      writeAuctionFilterParams(params, filters);
      params.set("page", "0");
    });
  };

  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <AuctionTrackingView
          initialPage={initialPage}
          initialSummary={initialSummary}
          initialFilters={initialFilters}
          filters={auctionQuery.filters}
          queryFilters={auctionQuery.queryState.filters}
          queryPage={auctionQuery.queryState.page}
          querySize={auctionQuery.queryState.size}
          onFilterChange={auctionQuery.updateFilter}
          onPageChange={(page) => {
            auctionQuery.changePage(page);
            writeUrlParams((params) => {
              params.set("page", String(page));
            });
          }}
          onPageSizeChange={(size) => {
            auctionQuery.changePageSize(size);
            writeUrlParams((params) => {
              params.set("size", String(size));
              params.set("page", "0");
            });
          }}
          onResetFilters={() => {
            auctionQuery.reset();
            writeUrlParams((params) => {
              deleteParams(params, AUCTION_FILTER_KEYS);
              params.set("page", "0");
            });
          }}
          onSearch={applyFilters}
        />
      </TabLayout>
    </main>
  );
}
