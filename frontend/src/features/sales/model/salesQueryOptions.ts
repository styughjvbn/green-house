import type { PaymentTargetType } from "@/entities/farm/types";
import { infiniteQueryOptions, queryOptions } from "@tanstack/react-query";
import {
  getAuctionLots,
  getAuctionProceeds,
  getAuctionProceedsPage,
  getAuctionTrackingSummary,
  getBusinessPartnerPage,
  getBusinessPartnerOptions,
  getBusinessPartnerOption,
  getReceivedPaymentPage,
  getSalesSlip,
  getSalesSlipPage,
} from "../api/salesApi";
import type {
  SalesRouteState,
  ProceedsRouteState,
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

export function businessPartnerOptionsQueryOptions(
  keyword = "",
  page = 0,
  auctionHouse?: boolean,
  active?: boolean,
) {
  const normalized = keyword.trim();
  return queryOptions({
    queryKey: salesQueryKeys.partners.options(
      normalized,
      page,
      auctionHouse,
      active,
    ),
    queryFn: ({ signal }) =>
      getBusinessPartnerOptions(normalized, page, auctionHouse, active, signal),
  });
}

export function businessPartnerOptionQueryOptions(id: number) {
  return queryOptions({
    queryKey: salesQueryKeys.partners.option(id),
    queryFn: ({ signal }) => getBusinessPartnerOption(id, signal),
    staleTime: 30_000,
  });
}

export function businessPartnerSearchQueryOptions(
  keyword = "",
  auctionHouse?: boolean,
  active?: boolean,
) {
  const normalized = keyword.trim();
  return infiniteQueryOptions({
    queryKey: salesQueryKeys.partners.searchOptions(
      normalized,
      auctionHouse,
      active,
    ),
    queryFn: ({ pageParam, signal }) =>
      getBusinessPartnerOptions(
        normalized,
        pageParam,
        auctionHouse,
        active,
        signal,
      ),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
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

export function auctionProceedsPageQueryOptions(state: ProceedsRouteState) {
  return queryOptions({
    queryKey: salesQueryKeys.auction.proceedsPage(state.page, state.size),
    queryFn: ({ signal }) =>
      getAuctionProceedsPage(state.page, state.size, signal),
  });
}

export function auctionProceedsDetailQueryOptions(id: number) {
  return queryOptions({
    queryKey: salesQueryKeys.auction.proceedsDetail(id),
    queryFn: ({ signal }) => getAuctionProceeds(id, signal),
  });
}

export function receivedPaymentPageQueryOptions(
  targetType: PaymentTargetType,
  targetId: number,
  page: number,
) {
  const size = 10;
  return queryOptions({
    queryKey: salesQueryKeys.payments.receivedPage(
      targetType,
      targetId,
      page,
      size,
    ),
    queryFn: ({ signal }) =>
      getReceivedPaymentPage(targetType, targetId, page, size, signal),
  });
}
