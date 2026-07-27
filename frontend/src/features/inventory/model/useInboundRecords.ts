import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { House } from "@/entities/farm/types";
import { createEmptyPage, type Page } from "@/shared/api/page";
import { usePagedListQuery } from "@/shared/api/usePagedListQuery";
import {
  cancelInboundRecord,
  createInboundRecord,
  deleteInboundRecord,
  getInboundRecords,
  getInventoryHouses,
  getVarietyGenera,
  potInboundRecord,
  updateInboundRecord,
} from "../api/inventoryApi";
import { createEmptyInboundFilters } from "../lib/inventoryUrlFilters";
import { inventoryQueryKeys } from "./inventoryQueryKeys";
import type {
  InboundFilterState,
  InboundPottingPayload,
  InboundRecord,
  InboundRecordPayload,
  InboundRecordUpdatePayload,
  VarietyLookup,
} from "./types";

export function useInboundRecords({
  initialFilters,
  initialLookup,
  initialPage,
}: {
  initialFilters: InboundFilterState;
  initialLookup: VarietyLookup;
  initialPage: Page<InboundRecord>;
}) {
  const queryClient = useQueryClient();
  const listState = usePagedListQuery({
    createEmptyFilters: createEmptyInboundFilters,
    initialFilters,
    initialPage,
    queryKey: ({ filters, page, size }) =>
      inventoryQueryKeys.inbound.page(filters, page, size),
    queryFn: ({ filters, page, size }) =>
      getInboundRecords({
        inboundType: filters.inboundType || undefined,
        status: filters.status || undefined,
        variety: filters.keyword || undefined,
        page,
        size,
      }),
  });
  const pageData =
    listState.pageData ??
    createEmptyPage<InboundRecord>(listState.queryState.size);
  const [selectedId, setSelectedId] = useState<number | null>(
    initialPage.content[0]?.id ?? null,
  );
  const selected =
    pageData.content.find((item) => item.id === selectedId) ??
    pageData.content[0] ??
    null;
  const lookupQuery = useQuery({
    queryKey: inventoryQueryKeys.varieties.lookup,
    queryFn: getVarietyGenera,
    initialData: initialLookup,
  });
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
    pageData,
    selected,
    selectedId: selected?.id ?? null,
    varietyOptions: lookupQuery.data.varieties,
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
      listState.query.isFetching ||
      createMutation.isPending ||
      updateMutation.isPending ||
      pottingMutation.isPending ||
      cancelMutation.isPending ||
      deleteMutation.isPending,
    housesLoading: housesQuery.isFetching,
    housesError: toMessage(housesQuery.error),
    error: toMessage(
      listState.query.error ??
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
