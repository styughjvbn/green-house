import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { House } from "@/entities/farm/types";
import { invalidateWorkAndInboundQueries } from "@/entities/farm/model/farmMutationQueries";
import { createEmptyPage } from "@/shared/api/page";
import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
import {
  cancelInboundRecord,
  createInboundRecord,
  getInventoryHouses,
  potInboundRecord,
  updateInboundRecord,
  voidInboundPotting,
} from "../api/inventoryApi";
import { createUuid } from "@/shared/lib/id";
import { createPendingCreationRequestKey } from "@/shared/lib/pendingCreationRequestKey";
import type { InboundRouteState } from "../lib/inventoryRouteState";
import {
  createEmptyInboundFilters,
  INBOUND_FILTER_KEYS,
  writeInboundFilterParams,
} from "../lib/inventoryUrlFilters";
import {
  inboundPageQueryOptions,
  inboundRecordQueryOptions,
  varietyLookupQueryOptions,
} from "./inventoryQueryOptions";
import { inventoryQueryKeys } from "./inventoryQueryKeys";
import type {
  InboundPottingPayload,
  InboundRecord,
  InboundRecordPayload,
  InboundRecordUpdatePayload,
} from "./types";

export function useInboundRecords({
  routeState,
}: {
  routeState: InboundRouteState;
}) {
  const queryClient = useQueryClient();
  const [creationRequestKey] = useState(() =>
    createPendingCreationRequestKey(
      "greenhouse:inbound-create-request:v1",
      createUuid,
      () => window.sessionStorage,
    ),
  );
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
  const [selectedId, setSelectedId] = useState<number | null>(
    routeState.selectedId,
  );
  const selectedInPage =
    pageData.content.find((item) => item.id === selectedId) ?? null;
  const selectedQuery = useQuery({
    ...inboundRecordQueryOptions(selectedId ?? 0),
    enabled: selectedId != null && selectedInPage == null,
  });
  const selected =
    selectedInPage ?? selectedQuery.data ?? pageData.content[0] ?? null;
  const lookupQuery = useQuery(varietyLookupQueryOptions());
  const [housesEnabled, setHousesEnabled] = useState(false);
  const housesQuery = useQuery<House[]>({
    queryKey: inventoryQueryKeys.houses,
    queryFn: getInventoryHouses,
    enabled: housesEnabled,
  });

  async function invalidate() {
    await invalidateWorkAndInboundQueries(queryClient);
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
    mutationFn: async (payload: InboundRecordPayload) => {
      const key = creationRequestKey.get();
      const created = await createInboundRecord(payload, key);
      // Acknowledgment precedes cache refresh, which may fail independently of the write.
      creationRequestKey.complete(key);
      return created;
    },
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
      idempotencyKey,
    }: {
      inboundRecordId: number;
      memo?: string;
      idempotencyKey: string;
    }) => cancelInboundRecord(inboundRecordId, idempotencyKey, memo),
    onSuccess: async (updated) => {
      setSelectedId(updated.id);
      await invalidate();
    },
  });
  const voidPottingMutation = useMutation({
    mutationFn: ({
      inboundRecordId,
      reason,
      idempotencyKey,
    }: {
      inboundRecordId: number;
      reason: string;
      idempotencyKey: string;
    }) => voidInboundPotting(inboundRecordId, { idempotencyKey, reason }),
    onSuccess: async (updated) => {
      setSelectedId(updated.id);
      await invalidateRelatedInventory();
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
      await cancelMutation.mutateAsync({
        inboundRecordId,
        memo,
        idempotencyKey:
          `inbound-cancel-${inboundRecordId}-${createUuid()}`.slice(0, 100),
      });
    },
    voidPotting: async (inboundRecordId: number, reason: string) => {
      await voidPottingMutation.mutateAsync({
        inboundRecordId,
        reason,
        idempotencyKey:
          `inbound-potting-void-${inboundRecordId}-${createUuid()}`.slice(
            0,
            100,
          ),
      });
    },
    loading:
      query.isFetching ||
      selectedQuery.isFetching ||
      createMutation.isPending ||
      updateMutation.isPending ||
      pottingMutation.isPending ||
      cancelMutation.isPending ||
      voidPottingMutation.isPending,
    housesLoading: housesQuery.isFetching,
    housesError: toMessage(housesQuery.error),
    error: toMessage(
      query.error ??
        selectedQuery.error ??
        lookupQuery.error ??
        createMutation.error ??
        updateMutation.error ??
        pottingMutation.error ??
        cancelMutation.error ??
        voidPottingMutation.error,
    ),
  };
}

function toMessage(error: unknown) {
  if (error == null) return null;
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
