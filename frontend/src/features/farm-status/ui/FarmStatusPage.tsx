import type {
  DashboardSummary,
  FarmStatusMapData,
  FarmStatusOrchidGroupList,
  FarmStatusZoomData,
} from "@/entities/farm/types";
import { FarmStatusMap } from "./FarmStatusMap";

type FarmStatusPageProps = {
  summary: DashboardSummary;
  mapData: FarmStatusMapData;
  initialSelection: FarmStatusOrchidGroupList | null;
  initialZoom: FarmStatusZoomData | null;
};

export function FarmStatusPage({
  summary,
  mapData,
  initialSelection,
  initialZoom,
}: FarmStatusPageProps) {
  return (
    <main className="space-y-5">
      <section className="grid gap-4 md:grid-cols-3 xl:grid-cols-6">
        <SummaryCard
          label="동 수"
          value={`${summary.houseCount}개`}
          tone="green"
          symbol="▣"
        />
        <SummaryCard
          label="물리 배드 수"
          value={`${summary.physicalBedCount}개`}
          tone="green"
          symbol="▤"
        />
        <SummaryCard
          label="논리 구역 수"
          value={`${summary.bedZoneCount}개`}
          tone="blue"
          symbol="▥"
        />
        <SummaryCard
          label="난 묶음 수"
          value={`${summary.orchidGroupCount}개`}
          tone="green"
          symbol="●"
        />
        <SummaryCard
          label="분갈이 예정"
          value={`${summary.repotDueCount}개`}
          tone="orange"
          symbol="!"
        />
        <SummaryCard
          label="상태 이상"
          value={`${summary.warningCount}개`}
          tone="red"
          symbol="!"
        />
      </section>

      <FarmStatusMap
        mapData={mapData}
        initialSelection={initialSelection}
        initialZoom={initialZoom}
      />
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
        <span
          className={`flex h-10 w-10 items-center justify-center rounded-md bg-[#f3f7f1] text-2xl font-semibold ${toneClass}`}
        >
          {symbol}
        </span>
      </div>
    </div>
  );
}
