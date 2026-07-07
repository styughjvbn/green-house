import type { House } from "@/entities/farm/types";
import {
  getInboundRecords,
  getMaterials,
  getVarieties,
  InventoryPage,
} from "@/features/inventory";
import { fetchApi } from "@/shared/api/client";

export const dynamic = "force-dynamic";

export default async function Page({
  searchParams,
}: {
  searchParams?: Promise<{ tab?: string }>;
}) {
  const resolvedSearchParams = searchParams ? await searchParams : undefined;
  const [varieties, inboundRecords, materials, houses] = await Promise.all([
    getVarieties(),
    getInboundRecords(),
    getMaterials(),
    fetchApi<House[]>("/houses"),
  ]);

  return (
    <InventoryPage
      initialActiveTab={resolvedSearchParams?.tab}
      houses={houses}
      initialInboundRecords={inboundRecords}
      initialMaterials={materials}
      initialVarieties={varieties}
    />
  );
}
