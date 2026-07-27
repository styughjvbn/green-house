import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { createEmptyPage, type Page } from "@/shared/api/page";
import { usePagedListQuery } from "@/shared/api/usePagedListQuery";
import {
  createVariety,
  deactivateVariety,
  deleteVariety,
  getVarieties,
  getVarietyGenera,
  getVarietyOrchidGroups,
  updateVariety,
} from "../api/inventoryApi";
import { createEmptyVarietyFilters } from "../lib/inventoryUrlFilters";
import { inventoryQueryKeys } from "./inventoryQueryKeys";
import type {
  Variety,
  VarietyFilterState,
  VarietyLookup,
  VarietyPayload,
} from "./types";

export function useVarieties({
  initialFilters,
  initialLookup,
  initialPage,
}: {
  initialFilters: VarietyFilterState;
  initialLookup: VarietyLookup;
  initialPage: Page<Variety>;
}) {
  const queryClient = useQueryClient();
  const listState = usePagedListQuery({
    createEmptyFilters: createEmptyVarietyFilters,
    initialFilters,
    initialPage,
    queryKey: ({ filters, page, size }) =>
      inventoryQueryKeys.varieties.page(filters, page, size),
    queryFn: ({ filters, page, size }) =>
      getVarieties({
        genus: filters.genus || undefined,
        keyword: filters.keyword || undefined,
        active: toActive(filters.status),
        saleEnabled: toBoolean(filters.saleEnabled),
        page,
        size,
      }),
  });
  const pageData =
    listState.pageData ?? createEmptyPage<Variety>(listState.queryState.size);
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
  const groupsQuery = useQuery({
    queryKey: inventoryQueryKeys.varieties.groups(selected?.id ?? 0),
    queryFn: () => getVarietyOrchidGroups(selected?.id as number),
    enabled: selected != null,
  });

  async function invalidate() {
    await queryClient.invalidateQueries({
      queryKey: inventoryQueryKeys.varieties.all,
    });
  }

  const createMutation = useMutation({
    mutationFn: createVariety,
    onSuccess: async (created) => {
      setSelectedId(created.id);
      await invalidate();
    },
  });
  const updateMutation = useMutation({
    mutationFn: ({
      varietyId,
      payload,
    }: {
      varietyId: number;
      payload: VarietyPayload;
    }) => updateVariety(varietyId, payload),
    onSuccess: async (updated) => {
      setSelectedId(updated.id);
      await invalidate();
    },
  });
  const deactivateMutation = useMutation({
    mutationFn: deactivateVariety,
    onSuccess: async (updated) => {
      setSelectedId(updated.id);
      await invalidate();
    },
  });
  const deleteMutation = useMutation({
    mutationFn: deleteVariety,
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
    lookup: lookupQuery.data,
    connectedGroups: groupsQuery.data ?? [],
    select: setSelectedId,
    create: async (payload: VarietyPayload) => {
      await createMutation.mutateAsync(payload);
    },
    update: async (varietyId: number, payload: VarietyPayload) => {
      await updateMutation.mutateAsync({ varietyId, payload });
    },
    deactivate: async (varietyId: number) => {
      await deactivateMutation.mutateAsync(varietyId);
    },
    remove: async (varietyId: number) => {
      await deleteMutation.mutateAsync(varietyId);
    },
    loading:
      listState.query.isFetching ||
      createMutation.isPending ||
      updateMutation.isPending ||
      deactivateMutation.isPending ||
      deleteMutation.isPending,
    groupsLoading: groupsQuery.isFetching,
    error: toMessage(
      listState.query.error ??
        lookupQuery.error ??
        groupsQuery.error ??
        createMutation.error ??
        updateMutation.error ??
        deactivateMutation.error ??
        deleteMutation.error,
    ),
  };
}

function toActive(status: VarietyFilterState["status"]) {
  return status ? status === "ACTIVE" : undefined;
}

function toBoolean(value: VarietyFilterState["saleEnabled"]) {
  return value ? value === "true" : undefined;
}

function toMessage(error: unknown) {
  if (error == null) return null;
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
