import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { createEmptyPage } from "@/shared/api/page";
import { useUrlPagedListState } from "@/shared/api/useUrlPagedListState";
import {
  createMaterial,
  deactivateMaterial,
  deleteMaterial,
  updateMaterial,
} from "../api/inventoryApi";
import type { InventoryRouteState } from "../lib/inventoryRouteState";
import {
  createEmptyMaterialFilters,
  MATERIAL_FILTER_KEYS,
  writeMaterialFilterParams,
} from "../lib/inventoryUrlFilters";
import { materialPageQueryOptions } from "./inventoryQueryOptions";
import { inventoryQueryKeys } from "./inventoryQueryKeys";
import type { Material, MaterialFilterState, MaterialPayload } from "./types";

export function useMaterials({
  routeState,
}: {
  routeState: InventoryRouteState<MaterialFilterState>;
}) {
  const queryClient = useQueryClient();
  const query = useQuery(materialPageQueryOptions(routeState));
  const listState = useUrlPagedListState({
    emptyFilters: createEmptyMaterialFilters,
    filterKeys: MATERIAL_FILTER_KEYS,
    routeFilters: routeState.filters,
    writeFilterParams: writeMaterialFilterParams,
  });
  const pageData =
    query.data ?? createEmptyPage<Material>(routeState.size, routeState.page);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const selected =
    pageData.content.find((item) => item.id === selectedId) ??
    pageData.content[0] ??
    null;

  async function invalidate() {
    await queryClient.invalidateQueries({
      queryKey: inventoryQueryKeys.materials.all,
    });
  }

  const createMutation = useMutation({
    mutationFn: createMaterial,
    onSuccess: async (created) => {
      setSelectedId(created.id);
      await invalidate();
    },
  });
  const updateMutation = useMutation({
    mutationFn: ({
      materialId,
      payload,
    }: {
      materialId: number;
      payload: MaterialPayload;
    }) => updateMaterial(materialId, payload),
    onSuccess: async (updated) => {
      setSelectedId(updated.id);
      await invalidate();
    },
  });
  const deactivateMutation = useMutation({
    mutationFn: deactivateMaterial,
    onSuccess: async (updated) => {
      setSelectedId(updated.id);
      await invalidate();
    },
  });
  const deleteMutation = useMutation({
    mutationFn: deleteMaterial,
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
    select: setSelectedId,
    create: async (payload: MaterialPayload) => {
      await createMutation.mutateAsync(payload);
    },
    update: async (materialId: number, payload: MaterialPayload) => {
      await updateMutation.mutateAsync({ materialId, payload });
    },
    deactivate: async (materialId: number) => {
      await deactivateMutation.mutateAsync(materialId);
    },
    remove: async (materialId: number) => {
      await deleteMutation.mutateAsync(materialId);
    },
    loading:
      query.isFetching ||
      createMutation.isPending ||
      updateMutation.isPending ||
      deactivateMutation.isPending ||
      deleteMutation.isPending,
    error: toMessage(
      query.error ??
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
