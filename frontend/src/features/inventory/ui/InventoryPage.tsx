"use client";

import { Download, Plus } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import type { House } from "@/entities/farm/types";
import {
  cancelInboundRecord,
  createInboundRecord,
  createVariety,
  getVarieties,
  getVarietyOrchidGroups,
  potInboundRecord,
} from "../api/inventoryApi";
import { INITIAL_MATERIALS } from "../lib/inventoryFixtures";
import type { InboundRecord, Material, Variety } from "../model/types";
import { InventoryDialog } from "./components/InventoryDialog";
import { InboundSection } from "./components/InboundSection";
import { MaterialSection } from "./components/MaterialSection";
import { VarietySection } from "./components/VarietySection";

export function InventoryPage({
  initialActiveTab,
  houses,
  initialInboundRecords,
  initialVarieties,
}: {
  initialActiveTab?: string;
  houses: House[];
  initialInboundRecords: InboundRecord[];
  initialVarieties: Variety[];
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [activeTab, setActiveTab] = useState<
    "VARIETY" | "INBOUND" | "MATERIAL"
  >(
    initialActiveTab === "INBOUND" || initialActiveTab === "MATERIAL"
      ? initialActiveTab
      : "VARIETY",
  );
  const [inboundRecords, setInboundRecords] = useState(initialInboundRecords);
  const [varieties, setVarieties] = useState(initialVarieties);
  const [materials, setMaterials] = useState(INITIAL_MATERIALS);
  const [selectedInboundId, setSelectedInboundId] = useState(
    initialInboundRecords[0]?.id ?? 0,
  );
  const [selectedVarietyId, setSelectedVarietyId] = useState(
    initialVarieties[0]?.id ?? 0,
  );
  const [selectedMaterialId, setSelectedMaterialId] = useState(materials[0].id);
  const [dialog, setDialog] = useState<"variety" | "material" | null>(null);
  const [loadingGroups, setLoadingGroups] = useState(false);

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

  useEffect(() => {
    const tab = searchParams.get("tab");
    if (tab === "VARIETY" || tab === "INBOUND" || tab === "MATERIAL") {
      setActiveTab(tab);
      return;
    }
    setActiveTab("VARIETY");
  }, [searchParams]);

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
      void (async () => {
        const item = await createVariety({
          genus: secondary,
          name,
          alias: "",
          defaultPotSize: "",
          saleEnabled: true,
          description: "",
          memo: "",
        });
        setVarieties((current) => [item, ...current]);
        setSelectedVarietyId(item.id);
      })();
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

  const updateTab = (nextTab: "VARIETY" | "INBOUND" | "MATERIAL") => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("tab", nextTab);
    router.replace(`${pathname}?${params.toString()}`);
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

      {activeTab === "VARIETY" ? (
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
            loadingGroups={loadingGroups}
            onSelect={setSelectedVarietyId}
          />
        </div>
      ) : null}

      {activeTab === "INBOUND" ? (
        <section className="rounded-md border border-[#dce2dc] bg-white p-6 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-sm font-bold">입고 관리</h2>
              <p className="mt-1 text-xs text-[#68756d]">
                다음 단계에서 입고 기록, 포트 작업, 초기 배치 구현 예정
              </p>
            </div>
            <button
              className="flex items-center gap-2 rounded-md border border-[#d7ddd8] px-4 py-2 text-sm font-semibold text-[#8a968e]"
              type="button"
              onClick={() => updateTab("INBOUND")}
            >
              <Plus className="h-4 w-4" />새 입고 등록
            </button>
          </div>
          <div className="mt-3">
            <InboundSection
              houses={houses}
              inboundRecords={inboundRecords}
              selectedId={selectedInboundId}
              varieties={varieties}
              onSelect={setSelectedInboundId}
              onCreate={async (payload) => {
                const created = await createInboundRecord(payload);
                setInboundRecords((current) => [created, ...current]);
                setSelectedInboundId(created.id);
                if (!payload.varietyId && payload.newVariety) {
                  setVarieties(await getVarieties());
                }
              }}
              onPotting={async (inboundRecordId, payload) => {
                const updated = await potInboundRecord(
                  inboundRecordId,
                  payload,
                );
                setInboundRecords((current) =>
                  current.map((item) =>
                    item.id === inboundRecordId ? updated : item,
                  ),
                );
              }}
              onCancel={async (inboundRecordId, memo) => {
                const updated = await cancelInboundRecord(
                  inboundRecordId,
                  memo,
                );
                setInboundRecords((current) =>
                  current.map((item) =>
                    item.id === inboundRecordId ? updated : item,
                  ),
                );
              }}
            />
          </div>
        </section>
      ) : null}

      {activeTab === "MATERIAL" ? (
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
      ) : null}

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
