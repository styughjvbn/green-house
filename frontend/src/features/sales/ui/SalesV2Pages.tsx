"use client";
import type { ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { TabLayout } from "@/shared/ui/TabLayout";
import { SalesAuctionPage } from "./SalesAuctionPage";
import { AuctionSettlementView } from "./auction/AuctionSettlementView";
import { PaymentAllocationWorkspace } from "./payment/PaymentAllocationWorkspace";
import { UnassignedReceiptView } from "./payment/UnassignedReceiptView";

export function SalesV2AuctionPage({ children }: { children?: ReactNode }) {
  const proceeds = useSearchParams().get("panel") === "proceeds";
  const write = useUrlSearchParamsWriter();
  return (
    <div className="flex h-full min-h-0 flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border bg-white px-4 py-3">
        <div>
          <h2 className="font-bold">
            {proceeds ? "경매 대금" : "경매 출하·결과·반환"}
          </h2>
          <p className="mt-1 text-sm text-[#68756c]">
            {proceeds
              ? "경매장 지급 안내액과 실제 입금을 대조합니다."
              : "출하 lot를 선택해 결과와 후속 처리·실제 반환 도착을 확인하세요."}
          </p>
        </div>
        <button
          type="button"
          className="rounded border px-4 py-2 text-sm"
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
        </button>
      </div>
      <div className="min-h-0 flex-1">
        {proceeds ? (
          <AuctionSettlementView v2 />
        ) : (
          (children ?? <SalesAuctionPage />)
        )}
      </div>
    </div>
  );
}
export function SalesV2PaymentsPage() {
  const allocations = useSearchParams().get("view") === "allocations";
  const write = useUrlSearchParamsWriter();
  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <section className="rounded-md border bg-white px-4 py-3">
          <h2 className="font-bold">입금</h2>
          <p className="mt-1 text-sm text-[#68756c]">
            실제 수납을 기록하고 일반 판매 전표·경매 대금에 배분합니다.
          </p>
        </section>
        <nav aria-label="입금 업무" className="flex flex-wrap gap-2">
          <button
            type="button"
            aria-pressed={!allocations}
            className="rounded border px-4 py-2"
            onClick={() => write((p) => p.delete("view"), "push")}
          >
            수납 등록·오입력 정정
          </button>
          <button
            type="button"
            aria-pressed={allocations}
            className="rounded border px-4 py-2"
            onClick={() => write((p) => p.set("view", "allocations"), "push")}
          >
            수납 조회·배분·정정
          </button>
        </nav>
        {allocations ? (
          <PaymentAllocationWorkspace />
        ) : (
          <UnassignedReceiptView />
        )}
      </TabLayout>
    </main>
  );
}
