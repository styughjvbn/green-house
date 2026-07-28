import { queryOptions } from "@tanstack/react-query";
import {
  getInboundRecords,
  getMaterials,
  getVarieties,
  getVarietyGenera,
} from "../api/inventoryApi";
import type { InventoryRouteState } from "../lib/inventoryRouteState";
import type {
  InboundFilterState,
  MaterialFilterState,
  VarietyFilterState,
} from "./types";
import { inventoryQueryKeys } from "./inventoryQueryKeys";

export function materialPageQueryOptions(
  state: InventoryRouteState<MaterialFilterState>,
) {
  return queryOptions({
    queryKey: inventoryQueryKeys.materials.page(
      state.filters,
      state.page,
      state.size,
    ),
    queryFn: () =>
      getMaterials({
        keyword: state.filters.keyword || undefined,
        category: state.filters.category || undefined,
        manufacturer: state.filters.manufacturer || undefined,
        active: toActive(state.filters.status),
        page: state.page,
        size: state.size,
      }),
  });
}

export function varietyPageQueryOptions(
  state: InventoryRouteState<VarietyFilterState>,
) {
  return queryOptions({
    queryKey: inventoryQueryKeys.varieties.page(
      state.filters,
      state.page,
      state.size,
    ),
    queryFn: () =>
      getVarieties({
        genus: state.filters.genus || undefined,
        keyword: state.filters.keyword || undefined,
        active: toActive(state.filters.status),
        saleEnabled: toBoolean(state.filters.saleEnabled),
        page: state.page,
        size: state.size,
      }),
  });
}

export function inboundPageQueryOptions(
  state: InventoryRouteState<InboundFilterState>,
) {
  return queryOptions({
    queryKey: inventoryQueryKeys.inbound.page(
      state.filters,
      state.page,
      state.size,
    ),
    queryFn: () =>
      getInboundRecords({
        inboundType: state.filters.inboundType || undefined,
        status: state.filters.status || undefined,
        variety: state.filters.keyword || undefined,
        page: state.page,
        size: state.size,
      }),
  });
}

export function varietyLookupQueryOptions() {
  return queryOptions({
    queryKey: inventoryQueryKeys.varieties.lookup,
    queryFn: getVarietyGenera,
  });
}

function toActive(status: "ACTIVE" | "INACTIVE" | "") {
  return status ? status === "ACTIVE" : undefined;
}

function toBoolean(value: "true" | "false" | "") {
  return value ? value === "true" : undefined;
}
