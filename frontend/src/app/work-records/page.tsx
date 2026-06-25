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
      <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
        <p className="text-sm font-semibold text-[#3d6f91]">작업 이력</p>
        <h1 className="mt-1 text-2xl font-semibold">작업 이력 관리</h1>
        <p className="mt-1 text-sm text-[#5c6a60]">
          농약, 비료, 분갈이, 정리 작업을 대상별로 기록합니다.
        </p>
      </section>
      <WorkRecordManager
        houses={mapData.houses}
        initialRecords={records}
        workTypes={workTypes}
      />
    </main>
  );
}
