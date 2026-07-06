import type { House } from "@/entities/farm/types";
import {
  getInboundRecords,
  getVarieties,
  InventoryPage,
} from "@/features/inventory";
import { fetchApi } from "@/shared/api/client";

export const dynamic = "force-dynamic";

export default async function Page() {
  const [varieties, inboundRecords, houses] = await Promise.all([
    getVarieties(),
    getInboundRecords(),
    fetchApi<House[]>("/houses"),
  ]);

  return (
    <InventoryPage
      houses={houses}
      initialInboundRecords={inboundRecords}
      initialVarieties={varieties}
    />
  );
}
