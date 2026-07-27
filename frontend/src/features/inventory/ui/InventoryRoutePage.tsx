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
  type ServerSearchParams,
} from "../lib/inventoryRouteState";
import {
  inboundPageQueryOptions,
  materialPageQueryOptions,
  varietyLookupQueryOptions,
  varietyPageQueryOptions,
} from "../model/inventoryQueryOptions";
import { InventoryInboundPage } from "./InventoryInboundPage";
import { InventoryMaterialPage } from "./InventoryMaterialPage";
import { InventoryVarietyPage } from "./InventoryVarietyPage";

export async function InventoryRoutePage({
  activeTab,
  searchParams,
}: {
  activeTab: InventoryTab;
  searchParams: Promise<ServerSearchParams>;
}) {
  const reader = createServerSearchParamReader(await searchParams);
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
