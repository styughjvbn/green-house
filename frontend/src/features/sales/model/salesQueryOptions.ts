import { queryOptions } from "@tanstack/react-query";
import {
  getAuctionLots,
  getAuctionSettlement,
  getAuctionSettlementPage,
  getAuctionSettlementSummary,
  getAuctionTrackingSummary,
  getBusinessPartnerPage,
  getBusinessPartners,
  getSalesSlip,
  getSalesSlipPage,
} from "../api/salesApi";
import type {
  SalesRouteState,
  SettlementRouteState,
} from "../lib/salesRouteParams";
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

export function salesSlipDetailQueryOptions(salesSlipId: number) {
  return queryOptions({
    queryKey: salesQueryKeys.slips.detail(salesSlipId),
    queryFn: () => getSalesSlip(salesSlipId),
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

export function auctionSettlementPageQueryOptions(state: SettlementRouteState) {
  return queryOptions({
    queryKey: salesQueryKeys.auction.settlementPage(state.page, state.size),
    queryFn: ({ signal }) =>
      getAuctionSettlementPage(state.page, state.size, signal),
  });
}

export function auctionSettlementSummaryQueryOptions() {
  return queryOptions({
    queryKey: salesQueryKeys.auction.settlementSummary,
    queryFn: ({ signal }) => getAuctionSettlementSummary(signal),
  });
}

export function auctionSettlementDetailQueryOptions(id: number) {
  return queryOptions({
    queryKey: salesQueryKeys.auction.settlementDetail(id),
    queryFn: ({ signal }) => getAuctionSettlement(id, signal),
  });
}
