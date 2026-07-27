import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { AuctionLotPage } from "@/entities/farm/types";
import { createInitialAuctionFilters } from "../lib/salesUrlFilters";
import type { AuctionFilterState } from "./types";

const auctionTrackingQueryKey = ["sales", "auctionTracking"] as const;

export function useAuctionTrackingQueryState({
  initialFilters,
  initialPage,
}: {
  initialFilters: AuctionFilterState;
  initialPage: AuctionLotPage;
}) {
  const queryClient = useQueryClient();
  const [queryState, setQueryState] = useState(() => ({
    filters: initialFilters,
    page: initialPage.page,
    size: initialPage.size,
  }));

  function search(filters: AuctionFilterState) {
    setQueryState((current) => ({ ...current, filters, page: 0 }));
    invalidate();
  }

  function reset() {
    setQueryState((current) => ({
      ...current,
      filters: createInitialAuctionFilters(),
      page: 0,
    }));
    invalidate();
  }

  function changePage(page: number) {
    setQueryState((current) => ({ ...current, page }));
  }

  function changePageSize(size: number) {
    setQueryState((current) => ({ ...current, size, page: 0 }));
  }

  function invalidate() {
    void queryClient.invalidateQueries({
      queryKey: auctionTrackingQueryKey,
    });
  }

  return {
    queryState,
    search,
    reset,
    changePage,
    changePageSize,
  };
}
