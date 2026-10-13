"use client";

import { useState, type ReactNode } from "react";
import { FilterResetButton } from "@/shared/ui/FilterControls";
import { hasFilterConditions } from "@/shared/ui/listFilterBadges";
import { useSearchParams } from "next/navigation";
import { TabError } from "@/shared/ui/TabLayout";
import { readMaterialRouteState } from "../lib/inventoryRouteState";
import { useMaterials } from "../model/useMaterials";
import type { MaterialPayload } from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
import { MaterialFilters } from "./material/MaterialFilters";
import { MaterialView } from "./material/MaterialView";

export function InventoryMaterialPage() {
  const routeState = readMaterialRouteState(useSearchParams());
  const materials = useMaterials({ routeState });
  const [dialogOpen, setDialogOpen] = useState(false);

  async function createMaterial(payload: MaterialPayload) {
    await materials.create(payload);
    materials.changePage(0);
    setDialogOpen(false);
  }

  const toolbar = (columnSettings: ReactNode) => (
    <MaterialFilters
      filters={materials.filters}
      appliedFilters={routeState.filters}
      columnSettings={columnSettings}
      loading={materials.loading}
      onChange={materials.updateFilter}
      onRemove={materials.removeFilters}
      onReset={materials.reset}
      onResetFilters={materials.resetPopupFilters}
      onSearch={materials.search}
    />
  );
  const emptyAction =
    materials.query.isSuccess &&
    !materials.loading &&
    !materials.error &&
    materials.pageData.totalElements === 0 ? (
      <FilterResetButton
        className="lg:mt-0"
        label="전체 초기화"
        onClick={materials.reset}
      />
    ) : undefined;

  return (
    <main className="flex h-full min-h-0 min-w-0 flex-col">
      <TabError message={materials.error} />
      <MaterialView
        hasAppliedFilters={hasFilterConditions(routeState.filters)}
        toolbar={toolbar}
        emptyAction={emptyAction}
        loading={materials.loading}
        pageData={materials.pageData}
        selectedId={materials.selectedId ?? 0}
        onCreate={() => setDialogOpen(true)}
        onDeactivate={materials.deactivate}
        onDelete={materials.remove}
        onPageChange={materials.changePage}
        onPageSizeChange={materials.changePageSize}
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
