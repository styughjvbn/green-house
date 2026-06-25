import Link from "next/link";
import { toneBorder } from "../../lib/dashboardView";
import { DashboardPanel } from "./DashboardPanel";

export function TodayChecklist({
  repotDueCount,
  warningCount,
}: {
  repotDueCount: number;
  warningCount: number;
}) {
  const items = [
    ["상태 이상 난 묶음", warningCount, "분", "red"],
    ["분갈이 예정 (이번 주 내)", repotDueCount, "분", "orange"],
    ["최근 농약 기록이 오래된 동", 3, "개 동", "orange"],
    ["최근 비료 기록이 오래된 동", 2, "개 동", "orange"],
    ["작업 기록 누락 의심", 1, "건", "blue"],
  ] as const;

  return (
    <DashboardPanel title="오늘 확인 필요">
      <div className="space-y-2">
        {items.map(([label, count, unit, tone]) => (
          <div
            key={label}
            className="flex items-center gap-3 rounded-md border border-[#edf0ec] bg-white px-3 py-2.5"
          >
            <span
              className={`flex h-6 w-6 items-center justify-center rounded-full border text-sm ${toneBorder(tone)}`}
            >
              !
            </span>
            <span className="min-w-0 flex-1 text-sm font-semibold text-[#344138]">
              {label}
            </span>
            <span className="text-base font-bold text-[#17251b]">{count}</span>
            <span className="text-sm text-[#5c6a60]">{unit}</span>
            <Link
              className="rounded-md border border-[#dfe5dc] px-3 py-1.5 text-xs font-semibold"
              href="/farm-status"
            >
              확인하기
            </Link>
          </div>
        ))}
      </div>
    </DashboardPanel>
  );
}
