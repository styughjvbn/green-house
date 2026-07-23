import { notFound } from "next/navigation";
import type { House, WorkType } from "@/entities/farm/types";
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

  const [workTypes, houses] = await Promise.all([
    fetchApi<WorkType[]>("/work-types"),
    fetchApi<House[]>("/houses"),
  ]);

  return (
    <main className="h-full min-h-0">
      <WorkRecordManager
        activeTab={activeTab}
        houses={houses}
        key={activeTab}
        workTypes={workTypes}
      />
    </main>
  );
}
