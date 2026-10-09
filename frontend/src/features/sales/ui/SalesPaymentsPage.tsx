"use client";
import { useSearchParams } from "next/navigation";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { TabLayout } from "@/shared/ui/TabLayout";
import { Button } from "@/shared/ui/primitives/button";
import { PaymentAllocationWorkspace } from "./payment/PaymentAllocationWorkspace";
import { UnassignedReceiptView } from "./payment/UnassignedReceiptView";
export function SalesPaymentsPage() {
  const allocations = useSearchParams().get("view") === "allocations";
  const write = useUrlSearchParamsWriter();
  return (
    <main className="h-full min-h-0">
      <TabLayout>
        <nav aria-label="수납 업무" className="flex flex-wrap gap-2">
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
          <UnassignedReceiptView dedicated />
        )}
      </TabLayout>
    </main>
  );
}
