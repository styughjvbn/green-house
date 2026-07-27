"use client";

import type { AuctionSettlement } from "@/entities/farm/types";
import { TabLayout } from "@/shared/ui/TabLayout";
import { AuctionSettlementView } from "./auction/AuctionSettlementView";

export function SalesSettlementPage({
  initialSettlements,
}: {
  initialSettlements: AuctionSettlement[];
}) {
  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <AuctionSettlementView initialSettlements={initialSettlements} />
      </TabLayout>
    </main>
  );
}
