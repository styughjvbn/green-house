import {
  dehydrate,
  HydrationBoundary,
  QueryClient,
} from "@tanstack/react-query";
import type { InventoryTab } from "@/shared/config/routes";
import {
  createServerSearchParamReader,
  readInboundRouteState,
  readMaterialRouteState,
  readVarietyRouteState,
} from "./lib/inventoryRouteState";
import {
  inboundPageQueryOptions,
  materialPageQueryOptions,
  varietyLookupQueryOptions,
  varietyPageQueryOptions,
} from "./model/inventoryQueryOptions";
import { InventoryInboundPage } from "./ui/InventoryInboundPage";
import { InventoryMaterialPage } from "./ui/InventoryMaterialPage";
import { InventoryVarietyPage } from "./ui/InventoryVarietyPage";

export async function InventoryRoutePage({
  activeTab,
  resolvedSearchParams,
}: {
  activeTab: InventoryTab;
  resolvedSearchParams: Record<string, string | string[] | undefined>;
}) {
  const reader = createServerSearchParamReader(resolvedSearchParams);
  const queryClient = new QueryClient();

  switch (activeTab) {
    case "variety": {
      await Promise.all([
        queryClient.prefetchQuery(
          varietyPageQueryOptions(readVarietyRouteState(reader)),
        ),
        queryClient.prefetchQuery(varietyLookupQueryOptions()),
      ]);
      return (
        <HydrationBoundary state={dehydrate(queryClient)}>
          <InventoryVarietyPage />
        </HydrationBoundary>
      );
    }
    case "inbound": {
      await Promise.all([
        queryClient.prefetchQuery(
          inboundPageQueryOptions(readInboundRouteState(reader)),
        ),
        queryClient.prefetchQuery(varietyLookupQueryOptions()),
      ]);
      return (
        <HydrationBoundary state={dehydrate(queryClient)}>
          <InventoryInboundPage />
        </HydrationBoundary>
      );
    }
    case "material": {
      await queryClient.prefetchQuery(
        materialPageQueryOptions(readMaterialRouteState(reader)),
      );
      return (
        <HydrationBoundary state={dehydrate(queryClient)}>
          <InventoryMaterialPage />
        </HydrationBoundary>
      );
    }
  }
}
