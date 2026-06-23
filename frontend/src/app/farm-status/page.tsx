import { FarmStatusMap } from "@/components/farm-map/farm-status-map";
import { fetchApi } from "@/lib/api";
import type { DashboardSummary, FarmStatusMapData, FarmStatusOrchidGroupList, FarmStatusZoomData } from "@/types/farm";

export const dynamic = "force-dynamic";

export default async function FarmStatusPage() {
  const summary = await fetchApi<DashboardSummary>("/dashboard/summary");
  const mapData = await fetchApi<FarmStatusMapData>("/farm-status/map");
  const firstHouse = mapData.houses[0];
  const initialSelection = firstHouse
    ? await fetchApi<FarmStatusOrchidGroupList>(`/farm-status/orchid-groups?targetType=HOUSE&targetId=${firstHouse.houseId}`)
    : null;
  const initialZoom = firstHouse
    ? await fetchApi<FarmStatusZoomData>(`/farm-status/zoom?level=HOUSE&houseId=${firstHouse.houseId}`)
    : null;

  return (
    <main className="space-y-8">
      <section className="flex flex-col gap-4 rounded-md border border-[#dfe6dc] bg-white p-5 shadow-sm lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="text-sm font-semibold text-[#2f6f3e]">농장 현황</p>
          <h1 className="mt-2 text-3xl font-semibold">전체 동 보기</h1>
          <p className="mt-2 max-w-3xl text-lg text-[#5c6a60]">
            15개 동 상태를 한눈에 확인하고, 선택한 범위의 난 묶음을 조회합니다.
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-base">
          <span className="rounded-md border border-[#dfe6dc] px-4 py-2"><span className="mr-2 text-[#1f9c4d]">●</span>정상</span>
          <span className="rounded-md border border-[#dfe6dc] px-4 py-2"><span className="mr-2 text-[#f59e0b]">●</span>주의</span>
          <span className="rounded-md border border-[#dfe6dc] px-4 py-2"><span className="mr-2 text-[#ef4444]">●</span>작업 필요</span>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-3 xl:grid-cols-6">
        <SummaryCard label="동 수" value={`${summary.houseCount}개`} tone="green" symbol="⌂" />
        <SummaryCard label="물리 배드 수" value={`${summary.physicalBedCount}개`} tone="green" symbol="▦" />
        <SummaryCard label="논리 구역 수" value={`${summary.bedZoneCount}개`} tone="blue" symbol="□" />
        <SummaryCard label="난 묶음 수" value={`${summary.orchidGroupCount}개`} tone="green" symbol="♧" />
        <SummaryCard label="분갈이 예정" value={`${summary.repotDueCount}개`} tone="orange" symbol="▣" />
        <SummaryCard label="상태 이상" value={`${summary.warningCount}개`} tone="red" symbol="△" />
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
    <div className="min-h-28 rounded-md border border-[#dfe6dc] bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-base font-medium text-[#4d5b51]">{label}</p>
          <p className="mt-3 text-3xl font-semibold text-[#1f2a24]">{value}</p>
        </div>
        <span className={`text-3xl font-semibold ${toneClass}`}>{symbol}</span>
      </div>
    </div>
  );
}
