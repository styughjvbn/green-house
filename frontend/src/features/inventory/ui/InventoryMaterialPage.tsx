"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  createMaterial,
  deactivateMaterial,
  deleteMaterial,
  updateMaterial,
} from "../api/inventoryApi";
import type {
  InventoryPageResult,
  Material,
  MaterialPayload,
} from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
import { MaterialSection } from "./components/MaterialSection";

export function InventoryMaterialPage({
  initialMaterialPage,
}: {
  initialMaterialPage: InventoryPageResult<Material>;
}) {
  const router = useRouter();
  const [selectedMaterialId, setSelectedMaterialId] = useState(
    initialMaterialPage.content[0]?.id ?? 0,
  );
  const [dialogOpen, setDialogOpen] = useState(false);

  const handleCreateMaterial = async (payload: MaterialPayload) => {
    const created = await createMaterial(payload);
    setSelectedMaterialId(created.id);
    router.refresh();
  };

  const handleUpdateMaterial = async (
    materialId: number,
    payload: MaterialPayload,
  ) => {
    await updateMaterial(materialId, payload);
    router.refresh();
  };

  const handleDeactivateMaterial = async (materialId: number) => {
    await deactivateMaterial(materialId);
    router.refresh();
  };

  const handleDeleteMaterial = async (materialId: number) => {
    await deleteMaterial(materialId);
    router.refresh();
  };

  return (
    <main className="flex h-full min-h-0 min-w-0 flex-col">
      <div id="material-management" className="h-full min-h-0">
        <MaterialSection
          pageData={initialMaterialPage}
          selectedId={selectedMaterialId}
          onCreate={() => setDialogOpen(true)}
          onDeactivate={handleDeactivateMaterial}
          onDelete={handleDeleteMaterial}
          onSelect={setSelectedMaterialId}
          onUpdate={handleUpdateMaterial}
        />
      </div>

      {dialogOpen ? (
        <InventoryDialog
          key="material"
          kind="material"
          open
          onClose={() => setDialogOpen(false)}
          onSubmit={(values) => {
            void handleCreateMaterial(values);
          }}
        />
      ) : null}
    </main>
  );
}
