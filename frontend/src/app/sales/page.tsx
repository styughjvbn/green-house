import { SalesManager } from "@/components/sales/sales-manager";
import { fetchApi } from "@/lib/api";
import type { Customer, SalesSlip } from "@/types/farm";

export const dynamic = "force-dynamic";

export default async function SalesPage() {
  const [customers, salesSlips] = await Promise.all([
    fetchApi<Customer[]>("/customers"),
    fetchApi<SalesSlip[]>("/sales-slips"),
  ]);

  return (
    <main className="space-y-5">
      <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
        <p className="text-sm font-semibold text-[#3d6f91]">판매 관리</p>
        <h1 className="mt-1 text-2xl font-semibold">판매 전표</h1>
        <p className="mt-1 text-sm text-[#5c6a60]">거래처와 판매 품목을 등록하고 전표 합계를 확인합니다.</p>
      </section>
      <SalesManager initialCustomers={customers} initialSalesSlips={salesSlips} />
    </main>
  );
}
