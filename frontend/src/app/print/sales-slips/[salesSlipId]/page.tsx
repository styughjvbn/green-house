import { notFound } from "next/navigation";
import { SalesSlipPrintView } from "@/components/print/sales-slip-print-view";
import { fetchApi } from "@/lib/api";
import type { SalesSlip } from "@/types/farm";

type SalesSlipPrintPageProps = {
  params: Promise<{
    salesSlipId: string;
  }>;
};

export const dynamic = "force-dynamic";

export default async function SalesSlipPrintPage({ params }: SalesSlipPrintPageProps) {
  const { salesSlipId } = await params;
  const parsedId = Number(salesSlipId);

  if (!Number.isInteger(parsedId) || parsedId <= 0) {
    notFound();
  }

  const slip = await fetchApi<SalesSlip>(`/sales-slips/${parsedId}/print`);

  return <SalesSlipPrintView slip={slip} />;
}
