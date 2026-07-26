"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { House } from "@/entities/farm/types";
import {
  cancelInboundRecord,
  createInboundRecord,
  deleteInboundRecord,
  potInboundRecord,
  updateInboundRecord,
} from "../api/inventoryApi";
import type {
  InboundRecord,
  InboundRecordUpdatePayload,
  InventoryPageResult,
  Variety,
} from "../model/types";
import { InboundSection } from "./components/InboundSection";

export function InventoryInboundPage({
  houses,
  initialInboundPage,
  varietyOptions,
}: {
  houses: House[];
  initialInboundPage: InventoryPageResult<InboundRecord>;
  varietyOptions: Variety[];
}) {
  const router = useRouter();
  const [selectedInboundId, setSelectedInboundId] = useState(
    initialInboundPage.content[0]?.id ?? 0,
  );

  const handleUpdateInboundRecord = async (
    inboundRecordId: number,
    payload: InboundRecordUpdatePayload,
  ) => {
    await updateInboundRecord(inboundRecordId, payload);
    router.refresh();
  };

  return (
    <main className="flex h-full min-h-0 min-w-0 flex-col">
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
    </main>
  );
}
