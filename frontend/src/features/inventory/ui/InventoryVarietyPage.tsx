"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { Page } from "@/shared/api/page";
import {
  createVariety,
  deactivateVariety,
  deleteVariety,
  getVarietyOrchidGroups,
  updateVariety,
} from "../api/inventoryApi";
import type { Variety, VarietyPayload } from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
import { VarietySection } from "./components/VarietySection";

export function InventoryVarietyPage({
  initialVarietyPage,
  varietyGenera,
  varietyOptions,
}: {
  initialVarietyPage: Page<Variety>;
  varietyGenera: string[];
  varietyOptions: Variety[];
}) {
  const router = useRouter();
  const [selectedVarietyId, setSelectedVarietyId] = useState(
    initialVarietyPage.content[0]?.id ?? 0,
  );
  const [dialogOpen, setDialogOpen] = useState(false);
  const [loadingGroups, setLoadingGroups] = useState(false);
  const [selectedConnectedGroups, setSelectedConnectedGroups] = useState<
    Variety["connectedGroups"]
  >([]);
  const visibleSelectedVarietyId = initialVarietyPage.content.some(
    (variety) => variety.id === selectedVarietyId,
  )
    ? selectedVarietyId
    : (initialVarietyPage.content[0]?.id ?? 0);

  useEffect(() => {
    if (!visibleSelectedVarietyId) {
      return;
    }

    let active = true;

    void (async () => {
      setSelectedConnectedGroups([]);
      setLoadingGroups(true);
      try {
        const groups = await getVarietyOrchidGroups(visibleSelectedVarietyId);
        if (!active) return;
        setSelectedConnectedGroups(groups);
      } finally {
        if (active) setLoadingGroups(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [visibleSelectedVarietyId]);

  const handleCreateVariety = async (payload: VarietyPayload) => {
    const created = await createVariety(payload);
    setSelectedVarietyId(created.id);
    router.refresh();
  };

  const handleUpdateVariety = async (
    varietyId: number,
    payload: VarietyPayload,
  ) => {
    await updateVariety(varietyId, payload);
    router.refresh();
  };

  const handleDeactivateVariety = async (varietyId: number) => {
    await deactivateVariety(varietyId);
    router.refresh();
  };

  const handleDeleteVariety = async (varietyId: number) => {
    await deleteVariety(varietyId);
    router.refresh();
  };

  return (
    <main className="flex h-full min-h-0 min-w-0 flex-col">
      <div id="variety-management" className="h-full min-h-0">
        <VarietySection
          connectedGroups={selectedConnectedGroups}
          loadingGroups={loadingGroups}
          pageData={initialVarietyPage}
          selectedId={visibleSelectedVarietyId}
          genera={varietyGenera}
          onCreate={() => setDialogOpen(true)}
          onDeactivate={handleDeactivateVariety}
          onDelete={handleDeleteVariety}
          onSelect={setSelectedVarietyId}
          onUpdate={handleUpdateVariety}
        />
      </div>

      {dialogOpen ? (
        <InventoryDialog
          key="variety"
          kind="variety"
          open
          varieties={varietyOptions}
          onClose={() => setDialogOpen(false)}
          onSubmit={(values) => {
            void handleCreateVariety(values);
          }}
        />
      ) : null}
    </main>
  );
}
