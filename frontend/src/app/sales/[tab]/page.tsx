import { notFound } from "next/navigation";
import { SalesRoutePage } from "@/features/sales/ui/SalesRoutePage";
import { isSalesTab } from "@/shared/config/routes";

export const dynamic = "force-dynamic";

export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ tab: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { tab } = await params;
  if (!isSalesTab(tab)) notFound();

  const resolvedSearchParams = await searchParams;
  const requestedCreateSlip = resolvedSearchParams?.createSlip;
  const createSlip =
    (Array.isArray(requestedCreateSlip)
      ? requestedCreateSlip[0]
      : requestedCreateSlip) === "1";

  return (
    <SalesRoutePage
      activeTab={tab}
      createSlip={createSlip}
      searchParams={resolvedSearchParams}
    />
  );
}
