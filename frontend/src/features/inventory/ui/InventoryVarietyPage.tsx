"use client";

import { useState } from "react";
import type { Page } from "@/shared/api/page";
import { usePagedListUrlActions } from "@/shared/api/usePagedListUrlActions";
import { TabError } from "@/shared/ui/TabLayout";
import {
  VARIETY_FILTER_KEYS,
  writeVarietyFilterParams,
} from "../lib/inventoryUrlFilters";
import { useVarieties } from "../model/useVarieties";
import type {
  Variety,
  VarietyFilterState,
  VarietyLookup,
  VarietyPayload,
} from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
import { VarietyView } from "./variety/VarietyView";

export function InventoryVarietyPage({
  initialFilters,
  initialVarietyPage,
  initialVarietyLookup,
}: {
  initialFilters: VarietyFilterState;
  initialVarietyPage: Page<Variety>;
  initialVarietyLookup: VarietyLookup;
}) {
  const varieties = useVarieties({
    initialFilters,
    initialLookup: initialVarietyLookup,
    initialPage: initialVarietyPage,
  });
  const listActions = usePagedListUrlActions({
    filters: varieties.filters,
    filterKeys: VARIETY_FILTER_KEYS,
    writeFilterParams: writeVarietyFilterParams,
    onSearch: varieties.search,
    onReset: varieties.reset,
    onPageChange: varieties.changePage,
    onPageSizeChange: varieties.changePageSize,
  });
  const [dialogOpen, setDialogOpen] = useState(false);

  async function createVariety(payload: VarietyPayload) {
    await varieties.create(payload);
    listActions.changePage(0);
    setDialogOpen(false);
  }

  return (
    <main className="flex h-full min-h-0 min-w-0 flex-col">
      <TabError message={varieties.error} />
      <VarietyView
        connectedGroups={varieties.connectedGroups}
        filters={varieties.filters}
        genera={varieties.lookup.genera}
        loading={varieties.loading}
        loadingGroups={varieties.groupsLoading}
        pageData={varieties.pageData}
        selectedId={varieties.selectedId ?? 0}
        onCreate={() => setDialogOpen(true)}
        onDeactivate={varieties.deactivate}
        onDelete={varieties.remove}
        onFilterChange={varieties.updateFilter}
        onPageChange={listActions.changePage}
        onPageSizeChange={listActions.changePageSize}
        onReset={listActions.reset}
        onSearch={listActions.search}
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
