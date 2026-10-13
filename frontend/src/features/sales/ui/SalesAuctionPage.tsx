"use client";

import type { ReactNode } from "react";
import { FilterResetButton } from "@/shared/ui/FilterControls";
import { useSearchParams } from "next/navigation";
import { TabError, TabLayout, TabSplit, TabStack } from "@/shared/ui/TabLayout";
import { readAuctionRouteState } from "../lib/salesRouteParams";
import { useAuctionTracking } from "../model/useAuctionTracking";
import { AuctionFilters } from "./auction/AuctionFilters";
import { AuctionLotDetail } from "./auction/AuctionLotDetail";
import { AuctionLotList } from "./auction/AuctionLotList";

export function SalesAuctionPage({ actions }: { actions?: ReactNode }) {
  const routeState = readAuctionRouteState(useSearchParams());
  const tracking = useAuctionTracking({ routeState });

  const toolbar = (columnSettings: ReactNode) => (
    <AuctionFilters
      filters={tracking.filters}
      appliedFilters={routeState.filters}
      columnSettings={columnSettings}
      onRemove={tracking.removeFilters}
      onResetFilters={tracking.resetPopupFilters}
      loading={tracking.loading}
      summary={tracking.summary}
      onChange={tracking.updateFilter}
      onSearch={tracking.search}
      onReset={tracking.resetFilters}
    />
  );

  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <TabStack>
          <TabError message={tracking.error} />
          <TabSplit
            columns="lg:grid-cols-[minmax(0,1.15fr)_minmax(420px,0.85fr)]"
            gap="gap-3"
          >
            <AuctionLotList
              toolbar={toolbar}
              emptyAction={
                tracking.hasEmptyResult && !tracking.error ? (
                  <FilterResetButton
                    className="lg:mt-0"
                    label="전체 초기화"
                    onClick={tracking.resetFilters}
                  />
                ) : undefined
              }
              actions={actions}
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
              arrivalPage={routeState.arrivalPage}
              onAdjust={tracking.adjustQuantity}
            />
          </TabSplit>
        </TabStack>
      </TabLayout>
    </main>
  );
}
