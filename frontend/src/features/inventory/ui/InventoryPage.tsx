"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { House } from "@/entities/farm/types";
import {
  cancelInboundRecord,
  createMaterial,
  createInboundRecord,
  createVariety,
  deactivateMaterial,
  deactivateVariety,
  deleteInboundRecord,
  deleteMaterial,
  deleteVariety,
  getVarietyOrchidGroups,
  potInboundRecord,
  updateInboundRecord,
  updateMaterial,
  updateVariety,
} from "../api/inventoryApi";
import type {
  InboundRecord,
  InboundRecordUpdatePayload,
  InventoryPageResult,
  Material,
  MaterialPayload,
  Variety,
  VarietyPayload,
} from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
import { InboundSection } from "./components/InboundSection";
import { MaterialSection } from "./components/MaterialSection";
import { VarietySection } from "./components/VarietySection";

export function InventoryPage({
  activeTab,
  houses,
  initialInboundPage,
  initialMaterialPage,
  initialVarietyPage,
  varietyGenera,
  varietyOptions,
}: {
  activeTab: "VARIETY" | "INBOUND" | "MATERIAL";
  houses: House[];
  initialInboundPage: InventoryPageResult<InboundRecord>;
  initialMaterialPage: InventoryPageResult<Material>;
  initialVarietyPage: InventoryPageResult<Variety>;
  varietyGenera: string[];
  varietyOptions: Variety[];
}) {
  const router = useRouter();
  const [selectedInboundId, setSelectedInboundId] = useState(
    initialInboundPage.content[0]?.id ?? 0,
  );
  const [selectedVarietyId, setSelectedVarietyId] = useState(
    initialVarietyPage.content[0]?.id ?? 0,
  );
  const [selectedMaterialId, setSelectedMaterialId] = useState(
    initialMaterialPage.content[0]?.id ?? 0,
  );
  const [dialog, setDialog] = useState<"variety" | "material" | null>(null);
  const [loadingGroups, setLoadingGroups] = useState(false);
  const [selectedConnectedGroups, setSelectedConnectedGroups] = useState<
    Variety["connectedGroups"]
  >([]);

  useEffect(() => {
    if (!selectedVarietyId) return;

    let active = true;

    void (async () => {
      setLoadingGroups(true);
      try {
        const groups = await getVarietyOrchidGroups(selectedVarietyId);
        if (!active) return;
        setSelectedConnectedGroups(groups);
      } finally {
        if (active) setLoadingGroups(false);
      }
    })();

    return () => {
      active = false;
    };
  }, [selectedVarietyId]);

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

  const handleUpdateInboundRecord = async (
    inboundRecordId: number,
    payload: InboundRecordUpdatePayload,
  ) => {
    await updateInboundRecord(inboundRecordId, payload);
    router.refresh();
  };

  return (
    <main className="flex h-full min-h-0 min-w-0 flex-col">
      {activeTab === "VARIETY" ? (
        <div id="variety-management" className="h-full min-h-0">
          <VarietySection
            connectedGroups={selectedConnectedGroups}
            loadingGroups={loadingGroups}
            pageData={initialVarietyPage}
            selectedId={selectedVarietyId}
            genera={varietyGenera}
            onCreate={() => setDialog("variety")}
            onDeactivate={handleDeactivateVariety}
            onDelete={handleDeleteVariety}
            onSelect={setSelectedVarietyId}
            onUpdate={handleUpdateVariety}
          />
        </div>
      ) : null}

      {activeTab === "INBOUND" ? (
        <InboundSection
          houses={houses}
          pageData={initialInboundPage}
          selectedId={selectedInboundId}
          varieties={varietyOptions}
          onCancel={async (inboundRecordId, memo) => {
            await cancelInboundRecord(inboundRecordId, memo);
            router.refresh();
          }}
          onCreate={async (payload) => {
            const created = await createInboundRecord(payload);
            setSelectedInboundId(created.id);
            router.refresh();
          }}
          onPotting={async (inboundRecordId, payload) => {
            await potInboundRecord(inboundRecordId, payload);
            router.refresh();
          }}
          onDelete={async (inboundRecordId) => {
            await deleteInboundRecord(inboundRecordId);
            router.refresh();
          }}
          onSelect={setSelectedInboundId}
          onUpdate={handleUpdateInboundRecord}
        />
      ) : null}

      {activeTab === "MATERIAL" ? (
        <div id="material-management" className="h-full min-h-0">
          <MaterialSection
            pageData={initialMaterialPage}
            selectedId={selectedMaterialId}
            onCreate={() => setDialog("material")}
            onDeactivate={handleDeactivateMaterial}
            onDelete={handleDeleteMaterial}
            onSelect={setSelectedMaterialId}
            onUpdate={handleUpdateMaterial}
          />
        </div>
      ) : null}

      {dialog === "variety" ? (
        <InventoryDialog
          key="variety"
          kind="variety"
          open
          varieties={varietyOptions}
          onClose={() => setDialog(null)}
          onSubmit={(values) => {
            void handleCreateVariety(values);
          }}
        />
      ) : null}

      {dialog === "material" ? (
        <InventoryDialog
          key="material"
          kind="material"
          open
          onClose={() => setDialog(null)}
          onSubmit={(values) => {
            void handleCreateMaterial(values);
          }}
        />
      ) : null}
    </main>
  );
}
