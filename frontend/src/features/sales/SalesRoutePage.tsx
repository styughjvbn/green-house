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
  readProceedsRouteState,
} from "./lib/salesRouteParams";
import {
  auctionLotPageQueryOptions,
  auctionProceedsPageQueryOptions,
  auctionProceedsDetailQueryOptions,
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
  v2 = false,
}: {
  activeTab: SalesTab;
  v2?: boolean;
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
          <SalesSlipsPage
            v2={v2}
            initialShowCreateSlip={readCreateSlip(reader)}
          />
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
      const state = readProceedsRouteState(reader);
      await Promise.all([
        queryClient.prefetchQuery(auctionProceedsPageQueryOptions(state)),
        ...(state.selectedProceedsId == null
          ? []
          : [
              queryClient.prefetchQuery(
                auctionProceedsDetailQueryOptions(state.selectedProceedsId),
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
          <SalesPartnersPage v2={v2} />
        </HydrationBoundary>
      );
    }
  }
}
