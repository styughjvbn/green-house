import { queryOptions } from "@tanstack/react-query";
import {
  getAuctionLots,
  getAuctionSettlements,
  getAuctionTrackingSummary,
  getBusinessPartnerPage,
  getBusinessPartners,
  getSalesSlipPage,
} from "../api/salesApi";
import type { SalesRouteState } from "../lib/salesRouteParams";
import type {
  AuctionFilterState,
  BusinessPartnerFilterState,
  SalesFilterState,
} from "./types";
import { salesQueryKeys } from "./salesQueryKeys";

export function salesSlipPageQueryOptions(
  state: SalesRouteState<SalesFilterState>,
) {
  return queryOptions({
    queryKey: salesQueryKeys.slips.page(state.filters, state.page, state.size),
    queryFn: () => getSalesSlipPage(state.filters, state.page, state.size),
  });
}

export function businessPartnerPageQueryOptions(
  state: SalesRouteState<BusinessPartnerFilterState>,
) {
  return queryOptions({
    queryKey: salesQueryKeys.partners.page(
      state.filters,
      state.page,
      state.size,
    ),
    queryFn: () =>
      getBusinessPartnerPage(state.filters, state.page, state.size),
  });
}

export function businessPartnerLookupQueryOptions() {
  return queryOptions({
    queryKey: salesQueryKeys.partners.lookup,
    queryFn: getBusinessPartners,
  });
}

export function auctionLotPageQueryOptions(
  state: SalesRouteState<AuctionFilterState>,
) {
  return queryOptions({
    queryKey: salesQueryKeys.auction.lots(
      state.filters,
      state.page,
      state.size,
    ),
    queryFn: () => getAuctionLots(state.filters, state.page, state.size),
  });
}

export function auctionSummaryQueryOptions() {
  return queryOptions({
    queryKey: salesQueryKeys.auction.summary,
    queryFn: getAuctionTrackingSummary,
  });
}

export function auctionSettlementsQueryOptions() {
  return queryOptions({
    queryKey: salesQueryKeys.auction.settlements,
    queryFn: () => getAuctionSettlements(),
  });
}
