"use client";

import { useSearchParams } from "next/navigation";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { UnassignedReceiptView } from "./payment/UnassignedReceiptView";
import { TabLayout } from "@/shared/ui/TabLayout";
import { AuctionSettlementView } from "./auction/AuctionSettlementView";

export function SalesSettlementPage() {
  const receipts = useSearchParams().get("view") === "receipts";
  const write = useUrlSearchParamsWriter();
  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <nav aria-label="입금 업무" className="flex gap-2">
          <button
            type="button"
            aria-pressed={!receipts}
            className="rounded border px-4 py-2"
            onClick={() => write((p) => p.delete("view"), "push")}
          >
            경매 대금
          </button>
          <button
            type="button"
            aria-pressed={receipts}
            className="rounded border px-4 py-2"
            onClick={() => write((p) => p.set("view", "receipts"), "push")}
          >
            대상 미지정 수납
          </button>
        </nav>
        {receipts ? <UnassignedReceiptView /> : <AuctionSettlementView />}
      </TabLayout>
    </main>
  );
}
