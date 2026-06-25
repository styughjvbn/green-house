import Link from "next/link";
import type { WorkRecord } from "@/entities/farm/types";
import { getRecentWorkLabel } from "../../lib/dashboardView";
import { DashboardEmptyText, DashboardPanel } from "./DashboardPanel";

export function RecentWorkSummary({ records }: { records: WorkRecord[] }) {
  return (
    <DashboardPanel title="최근 작업 요약">
      <div className="divide-y divide-[#edf0ec]">
        {records.map((record) => (
          <Link key={record.id} className="grid grid-cols-[1fr_110px_1fr_16px] items-center gap-3 py-3 text-sm" href="/work-records">
            <span className="font-semibold text-[#344138]">{record.workType}</span>
            <span className="text-[#5c6a60]">{record.workDate}</span>
            <span className="truncate text-[#5c6a60]">{getRecentWorkLabel(record)}</span>
            <span className="text-[#8a968f]">›</span>
          </Link>
        ))}
        {records.length === 0 ? <DashboardEmptyText text="최근 작업 이력이 없습니다." /> : null}
      </div>
      <Link className="mt-3 flex h-10 items-center justify-center rounded-md border border-[#dfe5dc] text-sm font-semibold text-[#344138]" href="/work-records">
        작업 이력 전체 보기
      </Link>
    </DashboardPanel>
  );
}
