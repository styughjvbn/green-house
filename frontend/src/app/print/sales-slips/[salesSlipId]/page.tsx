import { notFound } from "next/navigation";
import { SalesSlipPrintView } from "@/features/print/ui/SalesSlipPrintView";
import { fetchApi } from "@/shared/api/client";
import type { SalesSlip } from "@/entities/farm/types";

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
