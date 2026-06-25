import type { Customer, SalesSlip } from "@/entities/farm/types";
import { SalesManager } from "./SalesManager";

type SalesPageProps = {
  customers: Customer[];
  salesSlips: SalesSlip[];
};

export function SalesPage({ customers, salesSlips }: SalesPageProps) {
  return (
    <main className="space-y-5">
      <SalesManager
        initialCustomers={customers}
        initialSalesSlips={salesSlips}
      />
    </main>
  );
}
