import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { createEmptyPage } from "@/shared/api/page";
import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
import {
  createVariety,
  deactivateVariety,
  deleteVariety,
  getVarietyOrchidGroups,
  updateVariety,
} from "../api/inventoryApi";
import type { InventoryRouteState } from "../lib/inventoryRouteState";
import {
  createEmptyVarietyFilters,
  VARIETY_FILTER_KEYS,
  writeVarietyFilterParams,
} from "../lib/inventoryUrlFilters";
import {
  varietyLookupQueryOptions,
  varietyPageQueryOptions,
} from "./inventoryQueryOptions";
import { inventoryQueryKeys } from "./inventoryQueryKeys";
import type { Variety, VarietyFilterState, VarietyPayload } from "./types";

export function useVarieties({
  routeState,
}: {
  routeState: InventoryRouteState<VarietyFilterState>;
}) {
  const queryClient = useQueryClient();
  const query = useQuery(varietyPageQueryOptions(routeState));
  const listState = useUrlPagedListState({
    emptyFilters: createEmptyVarietyFilters,
    filterKeys: VARIETY_FILTER_KEYS,
    routeFilters: routeState.filters,
    writeFilterParams: writeVarietyFilterParams,
  });
  const pageData =
    query.data ?? createEmptyPage<Variety>(routeState.size, routeState.page);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const selected =
    pageData.content.find((item) => item.id === selectedId) ??
    pageData.content[0] ??
    null;
  const lookupQuery = useQuery(varietyLookupQueryOptions());
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
    query,
    pageData,
    selected,
    selectedId: selected?.id ?? null,
    lookup: lookupQuery.data ?? { genera: [], varieties: [] },
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
      query.isFetching ||
      createMutation.isPending ||
      updateMutation.isPending ||
      deactivateMutation.isPending ||
      deleteMutation.isPending,
    groupsLoading: groupsQuery.isFetching,
    error: toMessage(
      query.error ??
        lookupQuery.error ??
        groupsQuery.error ??
        createMutation.error ??
        updateMutation.error ??
        deactivateMutation.error ??
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
