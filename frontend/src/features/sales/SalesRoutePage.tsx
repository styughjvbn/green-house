import {
  dehydrate,
  HydrationBoundary,
  QueryClient,
} from "@tanstack/react-query";
import {
  createServerSearchParamReader,
  readAuctionRouteState,
  readBusinessPartnerRouteState,
  readCreateSlip,
  readSalesRouteState,
  readSettlementRouteState,
} from "./lib/salesRouteParams";
import {
  auctionLotPageQueryOptions,
  auctionSettlementPageQueryOptions,
  auctionSettlementSummaryQueryOptions,
  auctionSettlementDetailQueryOptions,
  auctionSummaryQueryOptions,
  businessPartnerOptionsQueryOptions,
  businessPartnerPageQueryOptions,
  salesSlipDetailQueryOptions,
  salesSlipPageQueryOptions,
} from "./model/salesQueryOptions";
import type { SalesTab } from "./model/types";
import { SalesAuctionPage } from "./ui/SalesAuctionPage";
import { SalesPartnersPage } from "./ui/SalesPartnersPage";
import { SalesSettlementPage } from "./ui/SalesSettlementPage";
import { SalesSlipsPage } from "./ui/SalesSlipsPage";

export async function SalesRoutePage({
  activeTab,
  resolvedSearchParams,
}: {
  activeTab: SalesTab;
  resolvedSearchParams: Record<string, string | string[] | undefined>;
}) {
  const reader = createServerSearchParamReader(resolvedSearchParams);
  const queryClient = new QueryClient();

  switch (activeTab) {
    case "slips": {
      const routeState = readSalesRouteState(reader);
      await Promise.all([
        queryClient.prefetchQuery(salesSlipPageQueryOptions(routeState)),
        queryClient.prefetchQuery(businessPartnerOptionsQueryOptions()),
        ...(routeState.selectedSlipId == null
          ? []
          : [
              queryClient.prefetchQuery(
                salesSlipDetailQueryOptions(routeState.selectedSlipId),
              ),
            ]),
      ]);
      return (
        <HydrationBoundary state={dehydrate(queryClient)}>
          <SalesSlipsPage initialShowCreateSlip={readCreateSlip(reader)} />
        </HydrationBoundary>
      );
    }
    case "auction": {
      await Promise.all([
        queryClient.prefetchQuery(
          auctionLotPageQueryOptions(readAuctionRouteState(reader)),
        ),
        queryClient.prefetchQuery(auctionSummaryQueryOptions()),
      ]);
      return (
        <HydrationBoundary state={dehydrate(queryClient)}>
          <SalesAuctionPage />
        </HydrationBoundary>
      );
    }
    case "settlement": {
      const state = readSettlementRouteState(reader);
      await Promise.all([
        queryClient.prefetchQuery(auctionSettlementPageQueryOptions(state)),
        queryClient.prefetchQuery(auctionSettlementSummaryQueryOptions()),
        ...(state.selectedSettlementId == null
          ? []
          : [
              queryClient.prefetchQuery(
                auctionSettlementDetailQueryOptions(state.selectedSettlementId),
              ),
            ]),
      ]);
      return (
        <HydrationBoundary state={dehydrate(queryClient)}>
          <SalesSettlementPage />
        </HydrationBoundary>
      );
    }
    case "partners": {
      await queryClient.prefetchQuery(
        businessPartnerPageQueryOptions(readBusinessPartnerRouteState(reader)),
      );
      return (
        <HydrationBoundary state={dehydrate(queryClient)}>
          <SalesPartnersPage />
        </HydrationBoundary>
      );
    }
  }
}
