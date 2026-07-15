import { notFound } from "next/navigation";
import type {
  FarmStatusMapData,
  WorkRecord,
  WorkType,
} from "@/entities/farm/types";
import { WorkRecordManager } from "@/features/work-record/ui/WorkRecordManager";
import { fetchApi } from "@/shared/api/client";

export const dynamic = "force-dynamic";

const WORK_TABS = {
  list: "LIST",
  calendar: "CALENDAR",
  history: "HISTORY",
} as const;

export default async function Page({
  params,
}: {
  params: Promise<{ tab: string }>;
}) {
  const { tab } = await params;
  const activeTab = WORK_TABS[tab as keyof typeof WORK_TABS];
  if (!activeTab) notFound();

  const [records, workTypes, mapData] = await Promise.all([
    fetchApi<WorkRecord[]>("/work-records"),
    fetchApi<WorkType[]>("/work-types"),
    fetchApi<FarmStatusMapData>("/farm-status/map"),
  ]);

  return (
    <main className="h-full min-h-0">
      <WorkRecordManager
        activeTab={activeTab}
        houses={mapData.houses}
        initialRecords={records}
        workTypes={workTypes}
      />
    </main>
  );
}
