"use client";

import { useState, type ReactNode } from "react";
import { FilterResetButton } from "@/shared/ui/FilterControls";
import { useSearchParams } from "next/navigation";
import { TabError } from "@/shared/ui/TabLayout";
import { readVarietyRouteState } from "../lib/inventoryRouteState";
import { useVarieties } from "../model/useVarieties";
import type { VarietyPayload } from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
import { VarietyFilters } from "./variety/VarietyFilters";
import { VarietyView } from "./variety/VarietyView";

export function InventoryVarietyPage() {
  const routeState = readVarietyRouteState(useSearchParams());
  const varieties = useVarieties({ routeState });
  const [dialogOpen, setDialogOpen] = useState(false);

  async function createVariety(payload: VarietyPayload) {
    await varieties.create(payload);
    varieties.changePage(0);
    setDialogOpen(false);
  }

  const toolbar = (columnSettings: ReactNode) => (
    <VarietyFilters
      filters={varieties.filters}
      appliedFilters={routeState.filters}
      columnSettings={columnSettings}
      loading={varieties.loading}
      genera={varieties.lookup.genera}
      onChange={varieties.updateFilter}
      onRemove={varieties.removeFilters}
      onReset={varieties.reset}
      onResetFilters={varieties.resetPopupFilters}
      onSearch={varieties.search}
    />
  );
  const emptyAction =
    varieties.query.isSuccess &&
    !varieties.loading &&
    !varieties.error &&
    varieties.pageData.totalElements === 0 ? (
      <FilterResetButton
        className="lg:mt-0"
        label="전체 초기화"
        onClick={varieties.reset}
      />
    ) : undefined;

  return (
    <main className="flex h-full min-h-0 min-w-0 flex-col">
      <TabError message={varieties.error} />
      <VarietyView
        toolbar={toolbar}
        emptyAction={emptyAction}
        connectedGroups={varieties.connectedGroups}
        loading={varieties.loading}
        loadingGroups={varieties.groupsLoading}
        pageData={varieties.pageData}
        selectedId={varieties.selectedId ?? 0}
        onCreate={() => setDialogOpen(true)}
        onDeactivate={varieties.deactivate}
        onDelete={varieties.remove}
        onPageChange={varieties.changePage}
        onPageSizeChange={varieties.changePageSize}
        onSelect={varieties.select}
        onUpdate={varieties.update}
      />

      <InventoryDialog
        key={dialogOpen ? "variety-open" : "variety-closed"}
        kind="variety"
        open={dialogOpen}
        varieties={varieties.lookup.varieties}
        onClose={() => setDialogOpen(false)}
        onSubmit={(values) => void createVariety(values)}
      />
    </main>
  );
}
