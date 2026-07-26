import { notFound } from "next/navigation";
import type { House, WorkType } from "@/entities/farm/types";
import { WorkRecordManager } from "@/features/work-record/ui/WorkRecordManager";
import { fetchApi } from "@/shared/api/client";
import { isWorkRecordTab } from "@/shared/config/routes";

export const dynamic = "force-dynamic";

export default async function Page({
  params,
}: {
  params: Promise<{ tab: string }>;
}) {
  const { tab } = await params;
  if (!isWorkRecordTab(tab)) notFound();

  const [workTypes, houses] = await Promise.all([
    fetchApi<WorkType[]>("/work-types"),
    fetchApi<House[]>("/houses"),
  ]);

  return (
    <main className="h-full min-h-0">
      <WorkRecordManager
        activeTab={tab}
        houses={houses}
        key={tab}
        workTypes={workTypes}
      />
    </main>
  );
}
