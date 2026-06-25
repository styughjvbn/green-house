import { WorkRecordManager } from "@/features/work-record/ui/WorkRecordManager";
import { fetchApi } from "@/shared/api/client";
import type { FarmStatusMapData, WorkRecord } from "@/entities/farm/types";

export const dynamic = "force-dynamic";

export default async function WorkRecordsPage() {
  const [records, workTypes, mapData] = await Promise.all([
    fetchApi<WorkRecord[]>("/work-records"),
    fetchApi<string[]>("/work-types"),
    fetchApi<FarmStatusMapData>("/farm-status/map"),
  ]);

  return (
    <main className="space-y-5">
      <WorkRecordManager
        houses={mapData.houses}
        initialRecords={records}
        workTypes={workTypes}
      />
    </main>
  );
}
