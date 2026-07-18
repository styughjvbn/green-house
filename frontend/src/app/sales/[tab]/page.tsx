import { notFound } from "next/navigation";
import type { SalesTab } from "@/features/sales/model/types";
import { SalesRoutePage } from "@/features/sales/ui/SalesRoutePage";

export const dynamic = "force-dynamic";

const SALES_TABS: Record<string, SalesTab> = {
  slips: "SLIPS",
  auction: "AUCTION",
  settlement: "SETTLEMENT",
  partners: "PARTNERS",
};

export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ tab: string }>;
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { tab } = await params;
  const activeTab = SALES_TABS[tab];
  if (!activeTab) notFound();

  const resolvedSearchParams = searchParams ? await searchParams : undefined;
  const requestedCreateSlip = resolvedSearchParams?.createSlip;
  const createSlip =
    (Array.isArray(requestedCreateSlip)
      ? requestedCreateSlip[0]
      : requestedCreateSlip) === "1";

  return <SalesRoutePage activeTab={activeTab} createSlip={createSlip} />;
}
