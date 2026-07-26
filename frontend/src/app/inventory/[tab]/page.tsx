import { notFound } from "next/navigation";
import { InventoryRoutePage } from "@/features/inventory/ui/InventoryRoutePage";
import { isInventoryTab } from "@/shared/config/routes";

export const dynamic = "force-dynamic";

export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ tab: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { tab } = await params;
  if (!isInventoryTab(tab)) notFound();

  return <InventoryRoutePage activeTab={tab} searchParams={searchParams} />;
}
