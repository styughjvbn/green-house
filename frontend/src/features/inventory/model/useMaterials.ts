import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { createEmptyPage, type Page } from "@/shared/api/page";
import { usePagedListQuery } from "@/shared/api/usePagedListQuery";
import {
  createMaterial,
  deactivateMaterial,
  deleteMaterial,
  getMaterials,
  updateMaterial,
} from "../api/inventoryApi";
import { createEmptyMaterialFilters } from "../lib/inventoryUrlFilters";
import { inventoryQueryKeys } from "./inventoryQueryKeys";
import type { Material, MaterialFilterState, MaterialPayload } from "./types";

export function useMaterials({
  initialFilters,
  initialPage,
}: {
  initialFilters: MaterialFilterState;
  initialPage: Page<Material>;
}) {
  const queryClient = useQueryClient();
  const listState = usePagedListQuery({
    createEmptyFilters: createEmptyMaterialFilters,
    initialFilters,
    initialPage,
    queryKey: ({ filters, page, size }) =>
      inventoryQueryKeys.materials.page(filters, page, size),
    queryFn: ({ filters, page, size }) =>
      getMaterials({
        category: filters.category || undefined,
        keyword: filters.keyword || undefined,
        manufacturer: filters.manufacturer || undefined,
        active: toActive(filters.status),
        page,
        size,
      }),
  });
  const pageData =
    listState.pageData ?? createEmptyPage<Material>(listState.queryState.size);
  const [selectedId, setSelectedId] = useState<number | null>(
    initialPage.content[0]?.id ?? null,
  );
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
      listState.query.isFetching ||
      createMutation.isPending ||
      updateMutation.isPending ||
      deactivateMutation.isPending ||
      deleteMutation.isPending,
    error: toMessage(
      listState.query.error ??
        createMutation.error ??
        updateMutation.error ??
        deactivateMutation.error ??
        deleteMutation.error,
    ),
  };
}

function toActive(status: MaterialFilterState["status"]) {
  return status ? status === "ACTIVE" : undefined;
}

function toMessage(error: unknown) {
  if (error == null) return null;
  return error instanceof Error
    ? error.message
    : "요청 중 문제가 발생했습니다.";
}
