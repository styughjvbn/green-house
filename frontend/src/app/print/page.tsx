import { getPrintableSalesSlips, PrintPage } from "@/features/print";

export const dynamic = "force-dynamic";

export default async function Page() {
  const salesSlips = await getPrintableSalesSlips();

  return <PrintPage salesSlips={salesSlips} />;
}
