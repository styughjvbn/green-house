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
} from "./lib/salesRouteParams";
import {
  auctionLotPageQueryOptions,
  auctionSettlementsQueryOptions,
  auctionSummaryQueryOptions,
  businessPartnerLookupQueryOptions,
  businessPartnerPageQueryOptions,
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
