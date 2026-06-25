import type { Customer, SalesSlip } from "@/entities/farm/types";
import { SalesManager } from "./SalesManager";

type SalesPageProps = {
  customers: Customer[];
  salesSlips: SalesSlip[];
};

export function SalesPage({ customers, salesSlips }: SalesPageProps) {
  return (
    <main className="space-y-5">
      <section className="flex items-end gap-5">
        <h1 className="text-2xl font-bold text-[#17251b]">판매 관리</h1>
        <p className="pb-1 text-sm text-[#5c6a60]">
          거래처 관리 및 판매 전표를 등록하고 관리합니다.
        </p>
      </section>
      <SalesManager
        initialCustomers={customers}
        initialSalesSlips={salesSlips}
      />
    </main>
  );
}
