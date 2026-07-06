"use client";

import { Download, Plus } from "lucide-react";
import { useState } from "react";
import { INITIAL_MATERIALS, INITIAL_VARIETIES } from "../lib/inventoryFixtures";
import type { Material, Variety } from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
import { MaterialSection } from "./components/MaterialSection";
import { VarietySection } from "./components/VarietySection";

export function InventoryPage() {
  const [varieties, setVarieties] = useState(INITIAL_VARIETIES);
  const [materials, setMaterials] = useState(INITIAL_MATERIALS);
  const [selectedVarietyId, setSelectedVarietyId] = useState(varieties[0].id);
  const [selectedMaterialId, setSelectedMaterialId] = useState(materials[0].id);
  const [dialog, setDialog] = useState<"variety" | "material" | null>(null);

  const exportCsv = () => {
    const rows = [
      "구분,코드,이름,상태",
      ...varieties.map(
        (item) => `품종,${item.code},${item.name},${item.status}`,
      ),
      ...materials.map(
        (item) => `자재,${item.code},${item.name},${item.status}`,
      ),
    ];
    const blob = new Blob([`\uFEFF${rows.join("\n")}`], {
      type: "text/csv;charset=utf-8",
    });
    const anchor = document.createElement("a");
    anchor.href = URL.createObjectURL(blob);
    anchor.download = "품종-자재-목록.csv";
    anchor.click();
    URL.revokeObjectURL(anchor.href);
  };

  const addItem = ({
    name,
    secondary,
  }: {
    name: string;
    secondary: string;
  }) => {
    if (dialog === "variety") {
      const id = Math.max(...varieties.map((item) => item.id)) + 1;
      const item: Variety = {
        id,
        code: `NEW-${String(id).padStart(4, "0")}`,
        genus: secondary,
        name,
        potSize: "미등록",
        saleEnabled: true,
        status: "ACTIVE",
        description: "",
        memo: "",
        registeredAt: new Date().toISOString().slice(0, 10),
        updatedAt: new Date().toISOString().slice(0, 10),
        connectedGroups: [],
      };
      setVarieties((current) => [...current, item]);
      setSelectedVarietyId(id);
      return;
    }

    const id = Math.max(...materials.map((item) => item.id)) + 1;
    const item: Material = {
      id,
      code: `NEW-${String(id).padStart(4, "0")}`,
      category: "자재",
      name,
      manufacturer: secondary,
      specification: "미등록",
      stockQuantity: "0",
      storageLocation: "미등록",
      usage: "",
      status: "ACTIVE",
      registeredAt: new Date().toISOString().slice(0, 10),
    };
    setMaterials((current) => [...current, item]);
    setSelectedMaterialId(id);
  };

  return (
    <main className="min-w-0 space-y-3">
      <div className="flex justify-end">
        <button
          className="flex items-center gap-2 rounded-md border border-[#d5dcd6] bg-white px-4 py-2 text-sm font-semibold shadow-sm"
          type="button"
          onClick={exportCsv}
        >
          <Download className="h-4 w-4" />
          내보내기
        </button>
      </div>

      <div id="variety-management">
        <div className="flex justify-end">
          <button
            className="flex items-center gap-2 rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white shadow-sm"
            type="button"
            onClick={() => setDialog("variety")}
          >
            <Plus className="h-4 w-4" />새 품종 등록
          </button>
        </div>
        <VarietySection
          varieties={varieties}
          selectedId={selectedVarietyId}
          onSelect={setSelectedVarietyId}
        />
      </div>

      <div id="material-management">
        <div className="flex justify-end">
          <button
            className="flex items-center gap-2 rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white"
            type="button"
            onClick={() => setDialog("material")}
          >
            <Plus className="h-4 w-4" />새 자재 등록
          </button>
        </div>
        <MaterialSection
          materials={materials}
          selectedId={selectedMaterialId}
          onSelect={setSelectedMaterialId}
        />
      </div>

      <InventoryDialog
        key={dialog ?? "closed"}
        kind={dialog ?? "variety"}
        open={dialog !== null}
        onClose={() => setDialog(null)}
        onSubmit={addItem}
      />
    </main>
  );
}
