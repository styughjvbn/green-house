"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type { House } from "@/entities/farm/types";
import {
  cancelInboundRecord,
  createMaterial,
  createInboundRecord,
  createVariety,
  deactivateMaterial,
  deactivateVariety,
  getVarieties,
  getVarietyOrchidGroups,
  potInboundRecord,
  updateInboundRecord,
  updateMaterial,
  updateVariety,
} from "../api/inventoryApi";
import type {
  InboundRecord,
  InboundRecordUpdatePayload,
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
  initialActiveTab,
  houses,
  initialInboundRecords,
  initialMaterials,
  initialVarieties,
}: {
  initialActiveTab?: string;
  houses: House[];
  initialInboundRecords: InboundRecord[];
  initialMaterials: Material[];
  initialVarieties: Variety[];
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [inboundRecords, setInboundRecords] = useState(initialInboundRecords);
  const [varieties, setVarieties] = useState(initialVarieties);
  const [materials, setMaterials] = useState(initialMaterials);
  const [selectedInboundId, setSelectedInboundId] = useState(
    initialInboundRecords[0]?.id ?? 0,
  );
  const [selectedVarietyId, setSelectedVarietyId] = useState(
    initialVarieties[0]?.id ?? 0,
  );
  const [selectedMaterialId, setSelectedMaterialId] = useState(
    initialMaterials[0]?.id ?? 0,
  );
  const [dialog, setDialog] = useState<"variety" | "material" | null>(null);
  const [loadingGroups, setLoadingGroups] = useState(false);
  const activeTab = useMemo<"VARIETY" | "INBOUND" | "MATERIAL">(() => {
    const tab = searchParams.get("tab");

    if (tab === "INBOUND" || tab === "MATERIAL" || tab === "VARIETY") {
      return tab;
    }

    if (initialActiveTab === "INBOUND" || initialActiveTab === "MATERIAL") {
      return initialActiveTab;
    }

    return "VARIETY";
  }, [initialActiveTab, searchParams]);

  useEffect(() => {
    if (!selectedVarietyId) return;
    let active = true;
    void (async () => {
      setLoadingGroups(true);
      try {
        const groups = await getVarietyOrchidGroups(selectedVarietyId);
        if (!active) return;
        setVarieties((current) =>
          current.map((item) =>
            item.id === selectedVarietyId
              ? { ...item, connectedGroups: groups }
              : item,
          ),
        );
      } finally {
        if (active) setLoadingGroups(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [selectedVarietyId]);

  const updateTab = (nextTab: "VARIETY" | "INBOUND" | "MATERIAL") => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", nextTab);
    router.replace(`${pathname}?${params.toString()}`);
  };

  const handleCreateVariety = async (payload: VarietyPayload) => {
    const item = await createVariety(payload);
    setVarieties((current) => [item, ...current]);
    setSelectedVarietyId(item.id);
  };

  const handleUpdateVariety = async (
    varietyId: number,
    payload: VarietyPayload,
  ) => {
    const updated = await updateVariety(varietyId, payload);
    setVarieties((current) =>
      current.map((item) =>
        item.id === varietyId ? { ...item, ...updated } : item,
      ),
    );
  };

  const handleDeactivateVariety = async (varietyId: number) => {
    const updated = await deactivateVariety(varietyId);
    setVarieties((current) =>
      current.map((item) =>
        item.id === varietyId ? { ...item, ...updated } : item,
      ),
    );
  };

  const handleCreateMaterial = async (payload: MaterialPayload) => {
    const item = await createMaterial(payload);
    setMaterials((current) => [item, ...current]);
    setSelectedMaterialId(item.id);
  };

  const handleUpdateMaterial = async (
    materialId: number,
    payload: MaterialPayload,
  ) => {
    const updated = await updateMaterial(materialId, payload);
    setMaterials((current) =>
      current.map((item) =>
        item.id === materialId ? { ...item, ...updated } : item,
      ),
    );
  };

  const handleDeactivateMaterial = async (materialId: number) => {
    const updated = await deactivateMaterial(materialId);
    setMaterials((current) =>
      current.map((item) =>
        item.id === materialId ? { ...item, ...updated } : item,
      ),
    );
  };

  const handleUpdateInboundRecord = async (
    inboundRecordId: number,
    payload: InboundRecordUpdatePayload,
  ) => {
    const updated = await updateInboundRecord(inboundRecordId, payload);
    setInboundRecords((current) =>
      current.map((item) => (item.id === inboundRecordId ? updated : item)),
    );
  };

  return (
    <main className="min-w-0 space-y-3">
      {activeTab === "VARIETY" ? (
        <div id="variety-management">
          <VarietySection
            varieties={varieties}
            selectedId={selectedVarietyId}
            loadingGroups={loadingGroups}
            onSelect={setSelectedVarietyId}
            onCreate={() => setDialog("variety")}
            onUpdate={handleUpdateVariety}
            onDeactivate={handleDeactivateVariety}
          />
        </div>
      ) : null}

      {activeTab === "INBOUND" ? (
        <InboundSection
          houses={houses}
          inboundRecords={inboundRecords}
          selectedId={selectedInboundId}
          varieties={varieties}
          onSelect={setSelectedInboundId}
          onOpenCreate={() => updateTab("INBOUND")}
          onUpdate={handleUpdateInboundRecord}
          onCreate={async (payload) => {
            const created = await createInboundRecord(payload);
            setInboundRecords((current) => [created, ...current]);
            setSelectedInboundId(created.id);
            if (!payload.varietyId && payload.newVariety) {
              setVarieties(await getVarieties());
            }
          }}
          onPotting={async (inboundRecordId, payload) => {
            const updated = await potInboundRecord(inboundRecordId, payload);
            setInboundRecords((current) =>
              current.map((item) =>
                item.id === inboundRecordId ? updated : item,
              ),
            );
          }}
          onCancel={async (inboundRecordId, memo) => {
            const updated = await cancelInboundRecord(inboundRecordId, memo);
            setInboundRecords((current) =>
              current.map((item) =>
                item.id === inboundRecordId ? updated : item,
              ),
            );
          }}
        />
      ) : null}

      {activeTab === "MATERIAL" ? (
        <div id="material-management">
          <MaterialSection
            materials={materials}
            selectedId={selectedMaterialId}
            onSelect={setSelectedMaterialId}
            onCreate={() => setDialog("material")}
            onUpdate={handleUpdateMaterial}
            onDeactivate={handleDeactivateMaterial}
          />
        </div>
      ) : null}

      {dialog === "variety" ? (
        <InventoryDialog
          key="variety"
          kind="variety"
          open
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
