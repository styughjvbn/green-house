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
  type RouteSearchParams,
} from "../lib/salesRouteParams";
import {
  auctionLotPageQueryOptions,
  auctionSettlementsQueryOptions,
  auctionSummaryQueryOptions,
  businessPartnerLookupQueryOptions,
  businessPartnerPageQueryOptions,
  salesSlipPageQueryOptions,
} from "../model/salesQueryOptions";
import type { SalesTab } from "../model/types";
import { SalesAuctionPage } from "./SalesAuctionPage";
import { SalesPartnersPage } from "./SalesPartnersPage";
import { SalesSettlementPage } from "./SalesSettlementPage";
import { SalesSlipsPage } from "./SalesSlipsPage";

export async function SalesRoutePage({
  activeTab,
  searchParams,
}: {
  activeTab: SalesTab;
  searchParams?: Promise<RouteSearchParams> | RouteSearchParams;
}) {
  const reader = createServerSearchParamReader(await searchParams);
  const queryClient = new QueryClient();

  switch (activeTab) {
    case "slips": {
      await Promise.all([
        queryClient.prefetchQuery(
          salesSlipPageQueryOptions(readSalesRouteState(reader)),
        ),
        queryClient.prefetchQuery(businessPartnerLookupQueryOptions()),
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
      await queryClient.prefetchQuery(auctionSettlementsQueryOptions());
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
