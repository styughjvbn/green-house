import { notFound } from "next/navigation";
import { getSalesSlipPrintData, SalesSlipPrintView } from "@/features/print";

type SalesSlipPrintPageProps = {
  params: Promise<{
    salesSlipId: string;
  }>;
};

export const dynamic = "force-dynamic";

export default async function Page({ params }: SalesSlipPrintPageProps) {
  const { salesSlipId } = await params;
  const parsedId = Number(salesSlipId);

  if (!Number.isInteger(parsedId) || parsedId <= 0) {
    notFound();
  }

  const slip = await getSalesSlipPrintData(parsedId);

  return <SalesSlipPrintView slip={slip} />;
}
