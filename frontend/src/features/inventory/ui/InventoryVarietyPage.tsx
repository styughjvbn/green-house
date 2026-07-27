"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { TabError } from "@/shared/ui/TabLayout";
import { readVarietyRouteState } from "../lib/inventoryRouteState";
import { useVarieties } from "../model/useVarieties";
import type { VarietyPayload } from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
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
        onPageChange={varieties.changePage}
        onPageSizeChange={varieties.changePageSize}
        onReset={varieties.reset}
        onSearch={varieties.search}
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
