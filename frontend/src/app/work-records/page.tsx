import type { House, WorkType } from "@/entities/farm/types";
import { WorkRecordManager } from "@/features/work-record/ui/WorkRecordManager";
import { fetchApi } from "@/shared/api/client";

export const dynamic = "force-dynamic";

export default async function Page() {
  const [workTypes, houses] = await Promise.all([
    fetchApi<WorkType[]>("/work-types"),
    fetchApi<House[]>("/houses"),
  ]);

  return (
    <main className="h-full min-h-0">
      <WorkRecordManager houses={houses} workTypes={workTypes} />
    </main>
  );
}
