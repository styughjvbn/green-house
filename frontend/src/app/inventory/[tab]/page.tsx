import { notFound } from "next/navigation";
import { InventoryRoutePage } from "@/features/inventory/ui/InventoryRoutePage";

export const dynamic = "force-dynamic";

const INVENTORY_TABS = {
  variety: "VARIETY",
  inbound: "INBOUND",
  material: "MATERIAL",
} as const;

export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ tab: string }>;
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { tab } = await params;
  const activeTab = INVENTORY_TABS[tab as keyof typeof INVENTORY_TABS];
  if (!activeTab) notFound();

  return (
    <InventoryRoutePage activeTab={activeTab} searchParams={searchParams} />
  );
}
