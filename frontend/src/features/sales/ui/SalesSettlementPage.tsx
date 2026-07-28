"use client";

import { TabLayout } from "@/shared/ui/TabLayout";
import { AuctionSettlementView } from "./auction/AuctionSettlementView";

export function SalesSettlementPage() {
  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <AuctionSettlementView />
      </TabLayout>
    </main>
  );
}
