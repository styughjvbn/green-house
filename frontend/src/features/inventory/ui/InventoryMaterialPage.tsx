"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { TabError } from "@/shared/ui/TabLayout";
import { readMaterialRouteState } from "../lib/inventoryRouteState";
import { useMaterials } from "../model/useMaterials";
import type { MaterialPayload } from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
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
        onPageChange={materials.changePage}
        onPageSizeChange={materials.changePageSize}
        onReset={materials.reset}
        onSearch={materials.search}
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
