import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { House } from "@/entities/farm/types";
import { createEmptyPage } from "@/shared/api/page";
import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
import {
  cancelInboundRecord,
  createInboundRecord,
  deleteInboundRecord,
  getInventoryHouses,
  potInboundRecord,
  updateInboundRecord,
} from "../api/inventoryApi";
import type { InventoryRouteState } from "../lib/inventoryRouteState";
import {
  createEmptyInboundFilters,
  INBOUND_FILTER_KEYS,
  writeInboundFilterParams,
} from "../lib/inventoryUrlFilters";
import {
  inboundPageQueryOptions,
  varietyLookupQueryOptions,
} from "./inventoryQueryOptions";
import { inventoryQueryKeys } from "./inventoryQueryKeys";
import type {
  InboundFilterState,
  InboundPottingPayload,
  InboundRecord,
  InboundRecordPayload,
  InboundRecordUpdatePayload,
} from "./types";

export function useInboundRecords({
  routeState,
}: {
  routeState: InventoryRouteState<InboundFilterState>;
}) {
  const queryClient = useQueryClient();
  const query = useQuery(inboundPageQueryOptions(routeState));
  const listState = useUrlPagedListState({
    emptyFilters: createEmptyInboundFilters,
    filterKeys: INBOUND_FILTER_KEYS,
    routeFilters: routeState.filters,
    writeFilterParams: writeInboundFilterParams,
  });
  const pageData =
    query.data ??
    createEmptyPage<InboundRecord>(routeState.size, routeState.page);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const selected =
    pageData.content.find((item) => item.id === selectedId) ??
    pageData.content[0] ??
    null;
  const lookupQuery = useQuery(varietyLookupQueryOptions());
  const [housesEnabled, setHousesEnabled] = useState(false);
  const housesQuery = useQuery<House[]>({
    queryKey: inventoryQueryKeys.houses,
    queryFn: getInventoryHouses,
    enabled: housesEnabled,
  });

  async function invalidate() {
    await queryClient.invalidateQueries({
      queryKey: inventoryQueryKeys.inbound.all,
    });
  }

  async function invalidateRelatedInventory() {
    await Promise.all([
      invalidate(),
      queryClient.invalidateQueries({
        queryKey: inventoryQueryKeys.varieties.all,
      }),
    ]);
  }

  const createMutation = useMutation({
    mutationFn: createInboundRecord,
    onSuccess: async (created) => {
      setSelectedId(created.id);
      await invalidateRelatedInventory();
    },
  });
  const updateMutation = useMutation({
    mutationFn: ({
      inboundRecordId,
      payload,
    }: {
      inboundRecordId: number;
      payload: InboundRecordUpdatePayload;
    }) => updateInboundRecord(inboundRecordId, payload),
    onSuccess: async (updated) => {
      setSelectedId(updated.id);
      await invalidate();
    },
  });
  const pottingMutation = useMutation({
    mutationFn: ({
      inboundRecordId,
      payload,
    }: {
      inboundRecordId: number;
      payload: InboundPottingPayload;
    }) => potInboundRecord(inboundRecordId, payload),
    onSuccess: invalidateRelatedInventory,
  });
  const cancelMutation = useMutation({
    mutationFn: ({
      inboundRecordId,
      memo,
    }: {
      inboundRecordId: number;
      memo?: string;
    }) => cancelInboundRecord(inboundRecordId, memo),
    onSuccess: async (updated) => {
      setSelectedId(updated.id);
      await invalidate();
    },
  });
  const deleteMutation = useMutation({
    mutationFn: deleteInboundRecord,
    onSuccess: async () => {
      setSelectedId(null);
      await invalidate();
    },
  });

  return {
    ...listState,
    query,
    pageData,
    selected,
    selectedId: selected?.id ?? null,
    varietyOptions: lookupQuery.data?.varieties ?? [],
    houses: housesQuery.data ?? [],
    enableHouses: () => setHousesEnabled(true),
    retryHouses: housesQuery.refetch,
    select: setSelectedId,
    create: async (payload: InboundRecordPayload) => {
      await createMutation.mutateAsync(payload);
    },
    update: async (
      inboundRecordId: number,
      payload: InboundRecordUpdatePayload,
    ) => {
      await updateMutation.mutateAsync({ inboundRecordId, payload });
    },
    pot: async (inboundRecordId: number, payload: InboundPottingPayload) => {
      await pottingMutation.mutateAsync({ inboundRecordId, payload });
    },
    cancel: async (inboundRecordId: number, memo?: string) => {
      await cancelMutation.mutateAsync({ inboundRecordId, memo });
    },
    remove: async (inboundRecordId: number) => {
      await deleteMutation.mutateAsync(inboundRecordId);
    },
    loading:
      query.isFetching ||
      createMutation.isPending ||
      updateMutation.isPending ||
      pottingMutation.isPending ||
      cancelMutation.isPending ||
      deleteMutation.isPending,
    housesLoading: housesQuery.isFetching,
    housesError: toMessage(housesQuery.error),
    error: toMessage(
      query.error ??
        lookupQuery.error ??
        createMutation.error ??
        updateMutation.error ??
        pottingMutation.error ??
        cancelMutation.error ??
        deleteMutation.error,
    ),
  };
}

function toMessage(error: unknown) {
  if (error == null) return null;
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
