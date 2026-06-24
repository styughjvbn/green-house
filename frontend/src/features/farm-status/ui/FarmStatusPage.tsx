import type { DashboardSummary, FarmStatusMapData, FarmStatusOrchidGroupList, FarmStatusZoomData } from "@/entities/farm/types";
import { FarmStatusMap } from "./components/FarmStatusMap";

export function FarmStatusPage({
  summary,
  mapData,
  initialSelection,
  initialZoom,
}: {
  summary: DashboardSummary;
  mapData: FarmStatusMapData;
  initialSelection: FarmStatusOrchidGroupList | null;
  initialZoom: FarmStatusZoomData | null;
}) {
  const todayLabel = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
  }).format(new Date());

  return (
    <main className="space-y-6">
      <section className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h1 className="text-3xl font-semibold tracking-normal text-[#17251b]">농장 현황</h1>
          <p className="mt-2 text-lg text-[#5d6d62]">전체 농장 구조와 난 묶음 현황을 한눈에 확인하세요.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2 text-sm text-[#405246]">
          <span className="relative rounded-md border border-[#d9e2d5] bg-white px-3 py-2 shadow-sm">
            알림
            <span className="ml-2 rounded-full bg-[#e63d32] px-1.5 py-0.5 text-xs font-semibold text-white">3</span>
          </span>
          <span className="rounded-md border border-[#d9e2d5] bg-white px-3 py-2 shadow-sm">{todayLabel}</span>
          <span className="rounded-md border border-[#d9e2d5] bg-white px-3 py-2 shadow-sm">24°C 흐림</span>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-3 xl:grid-cols-6">
        <SummaryCard label="동 수" value={`${summary.houseCount}개`} tone="green" symbol="▣" />
        <SummaryCard label="물리 배드 수" value={`${summary.physicalBedCount}개`} tone="green" symbol="▤" />
        <SummaryCard label="논리 구역 수" value={`${summary.bedZoneCount}개`} tone="blue" symbol="▥" />
        <SummaryCard label="난 묶음 수" value={`${summary.orchidGroupCount}개`} tone="green" symbol="●" />
        <SummaryCard label="분갈이 예정" value={`${summary.repotDueCount}개`} tone="orange" symbol="!" />
        <SummaryCard label="상태 이상" value={`${summary.warningCount}개`} tone="red" symbol="!" />
      </section>

      <FarmStatusMap mapData={mapData} initialSelection={initialSelection} initialZoom={initialZoom} />
    </main>
  );
}

function SummaryCard({
  label,
  value,
  tone,
  symbol,
}: {
  label: string;
  value: string;
  tone: "green" | "blue" | "orange" | "red";
  symbol: string;
}) {
  const toneClass = {
    green: "text-[#159447]",
    blue: "text-[#1976f3]",
    orange: "text-[#f28c00]",
    red: "text-[#e52d2d]",
  }[tone];

  return (
    <div className="min-h-28 rounded-lg border border-[#d9e2d5] bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-base font-medium text-[#4e6154]">{label}</p>
          <p className="mt-3 text-3xl font-semibold text-[#17251b]">{value}</p>
        </div>
        <span className={`flex h-10 w-10 items-center justify-center rounded-md bg-[#f3f7f1] text-2xl font-semibold ${toneClass}`}>
          {symbol}
        </span>
      </div>
    </div>
  );
}
