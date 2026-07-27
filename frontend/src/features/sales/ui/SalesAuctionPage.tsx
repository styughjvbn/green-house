"use client";

import { useSearchParams } from "next/navigation";
import { TabError, TabLayout, TabSplit, TabStack } from "@/shared/ui/TabLayout";
import { readAuctionRouteState } from "../lib/salesRouteParams";
import { useAuctionTracking } from "../model/useAuctionTracking";
import { AuctionFilters } from "./auction/AuctionFilters";
import { AuctionLotDetail } from "./auction/AuctionLotDetail";
import { AuctionLotList } from "./auction/AuctionLotList";

export function SalesAuctionPage() {
  const routeState = readAuctionRouteState(useSearchParams());
  const tracking = useAuctionTracking({ routeState });

  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <TabStack>
          <AuctionFilters
            filters={tracking.filters}
            loading={tracking.loading}
            summary={tracking.summary}
            onChange={tracking.updateFilter}
            onSearch={tracking.search}
            onReset={tracking.resetFilters}
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
              onPageChange={tracking.setPage}
              onPageSizeChange={tracking.setPageSize}
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
