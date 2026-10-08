"use client";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Wallet } from "lucide-react";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { TabLayout } from "@/shared/ui/TabLayout";
import { DetailActionButton } from "@/shared/ui/DetailCard";
import { Button } from "@/shared/ui/primitives/button";
import { SalesAuctionPage } from "./SalesAuctionPage";
import { AuctionSettlementView } from "./auction/AuctionSettlementView";
import { PaymentAllocationWorkspace } from "./payment/PaymentAllocationWorkspace";
import { UnassignedReceiptView } from "./payment/UnassignedReceiptView";
export function SalesV2AuctionPage() {
  const proceeds = useSearchParams().get("panel") === "proceeds";
  const write = useUrlSearchParamsWriter();
  const action = (
    <DetailActionButton
      icon={proceeds ? ArrowLeft : Wallet}
      onClick={() =>
        write((p) => {
          if (proceeds) {
            p.delete("panel");
            p.delete("paymentPage");
          } else p.set("panel", "proceeds");
        }, "push")
      }
    >
      {proceeds ? "출하 목록으로" : "경매 대금 확인"}
    </DetailActionButton>
  );
  return proceeds ? (
    <main className="h-full min-h-0">
      <TabLayout>
        <AuctionSettlementView v2 actions={action} />
      </TabLayout>
    </main>
  ) : (
    <SalesAuctionPage actions={action} />
  );
}
export function SalesV2PaymentsPage() {
  const allocations = useSearchParams().get("view") === "allocations";
  const write = useUrlSearchParamsWriter();
  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <nav aria-label="입금 업무" className="flex flex-wrap gap-2">
          <Button
            size="sm"
            variant={allocations ? "outline" : "default"}
            aria-label="수납 등록·오입력 정정"
            aria-pressed={!allocations}
            onClick={() => write((p) => p.delete("view"), "push")}
          >
            수납 기록
          </Button>
          <Button
            size="sm"
            variant={allocations ? "default" : "outline"}
            aria-label="수납 조회·배분·정정"
            aria-pressed={allocations}
            onClick={() => write((p) => p.set("view", "allocations"), "push")}
          >
            배분·정정
          </Button>
        </nav>
        {allocations ? (
          <PaymentAllocationWorkspace />
        ) : (
          <UnassignedReceiptView v2 />
        )}
      </TabLayout>
    </main>
  );
}
