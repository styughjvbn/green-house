import type { Customer, SalesSlip } from "@/entities/farm/types";
import { PageHeader } from "@/widgets/page-header";
import { SalesManager } from "./SalesManager";

type SalesPageProps = {
  customers: Customer[];
  salesSlips: SalesSlip[];
};

export function SalesPage({ customers, salesSlips }: SalesPageProps) {
  return (
    <main className="space-y-5">
      <PageHeader title="판매 관리" description="거래처와 판매 품목을 등록하고 전표 합계를 확인합니다." />
      <SalesManager
        initialCustomers={customers}
        initialSalesSlips={salesSlips}
      />
    </main>
  );
}
