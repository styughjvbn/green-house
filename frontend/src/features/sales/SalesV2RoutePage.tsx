import {
  dehydrate,
  HydrationBoundary,
  QueryClient,
} from "@tanstack/react-query";
import type { SalesV2Tab } from "@/shared/config/routes";
import { SalesRoutePage } from "./SalesRoutePage";
import {
  createServerSearchParamReader,
  readProceedsRouteState,
  readAuctionRouteState,
} from "./lib/salesRouteParams";
import {
  auctionLotPageQueryOptions,
  auctionSummaryQueryOptions,
  auctionProceedsPageQueryOptions,
  auctionProceedsDetailQueryOptions,
} from "./model/salesQueryOptions";
import { SalesV2AuctionPage, SalesV2PaymentsPage } from "./ui/SalesV2Pages";

export async function SalesV2RoutePage({
  activeTab,
  resolvedSearchParams,
}: {
  activeTab: SalesV2Tab;
  resolvedSearchParams: Record<string, string | string[] | undefined>;
}) {
  if (activeTab === "slips" || activeTab === "partners") {
    return (
      <SalesRoutePage
        v2
        activeTab={activeTab}
        resolvedSearchParams={resolvedSearchParams}
      />
    );
  }
  if (activeTab === "payments") return <SalesV2PaymentsPage />;
  const reader = createServerSearchParamReader(resolvedSearchParams);
  if (reader.get("panel") !== "proceeds") {
    const cache = new QueryClient();
    await Promise.all([
      cache.prefetchQuery(
        auctionLotPageQueryOptions(readAuctionRouteState(reader)),
      ),
      cache.prefetchQuery(auctionSummaryQueryOptions()),
    ]);
    return (
      <HydrationBoundary state={dehydrate(cache)}>
        <SalesV2AuctionPage />
      </HydrationBoundary>
    );
  }
  const state = readProceedsRouteState(reader, "auction");
  const cache = new QueryClient();
  await Promise.all([
    cache.prefetchQuery(auctionProceedsPageQueryOptions(state)),
    ...(state.selectedProceedsId == null
      ? []
      : [
          cache.prefetchQuery(
            auctionProceedsDetailQueryOptions(state.selectedProceedsId),
          ),
        ]),
  ]);
  return (
    <HydrationBoundary state={dehydrate(cache)}>
      <SalesV2AuctionPage />
    </HydrationBoundary>
  );
}
