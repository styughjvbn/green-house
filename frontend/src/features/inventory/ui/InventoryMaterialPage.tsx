"use client";

import { useState } from "react";
import type { Page } from "@/shared/api/page";
import { usePagedListUrlActions } from "@/shared/api/usePagedListUrlActions";
import { TabError } from "@/shared/ui/TabLayout";
import {
  MATERIAL_FILTER_KEYS,
  writeMaterialFilterParams,
} from "../lib/inventoryUrlFilters";
import { useMaterials } from "../model/useMaterials";
import type {
  Material,
  MaterialFilterState,
  MaterialPayload,
} from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
import { MaterialView } from "./material/MaterialView";

export function InventoryMaterialPage({
  initialFilters,
  initialMaterialPage,
}: {
  initialFilters: MaterialFilterState;
  initialMaterialPage: Page<Material>;
}) {
  const materials = useMaterials({
    initialFilters,
    initialPage: initialMaterialPage,
  });
  const listActions = usePagedListUrlActions({
    filters: materials.filters,
    filterKeys: MATERIAL_FILTER_KEYS,
    writeFilterParams: writeMaterialFilterParams,
    onSearch: materials.search,
    onReset: materials.reset,
    onPageChange: materials.changePage,
    onPageSizeChange: materials.changePageSize,
  });
  const [dialogOpen, setDialogOpen] = useState(false);

  async function createMaterial(payload: MaterialPayload) {
    await materials.create(payload);
    listActions.changePage(0);
    setDialogOpen(false);
  }

  return (
    <main className="flex h-full min-h-0 min-w-0 flex-col">
      <TabError message={materials.error} />
      <MaterialView
        filters={materials.filters}
        loading={materials.loading}
        pageData={materials.pageData}
        selectedId={materials.selectedId ?? 0}
        onCreate={() => setDialogOpen(true)}
        onDeactivate={materials.deactivate}
        onDelete={materials.remove}
        onFilterChange={materials.updateFilter}
        onPageChange={listActions.changePage}
        onPageSizeChange={listActions.changePageSize}
        onReset={listActions.reset}
        onSearch={listActions.search}
        onSelect={materials.select}
        onUpdate={materials.update}
      />

      <InventoryDialog
        key={dialogOpen ? "material-open" : "material-closed"}
        kind="material"
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        onSubmit={(values) => void createMaterial(values)}
      />
    </main>
  );
}
