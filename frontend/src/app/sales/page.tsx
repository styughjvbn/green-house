import { getCustomers, getSalesSlips, SalesPage } from "@/features/sales";

export const dynamic = "force-dynamic";

export default async function Page() {
  const [customers, salesSlips] = await Promise.all([
    getCustomers(),
    getSalesSlips(),
  ]);

  return <SalesPage customers={customers} salesSlips={salesSlips} />;
}
