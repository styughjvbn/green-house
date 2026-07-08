import {
  AlertTriangle,
  CalendarDays,
  Flower2,
  Home,
  Map,
  SquareDashed,
  type LucideIcon,
} from "lucide-react";
import type { DashboardSummary } from "@/entities/farm/types";
import { toneText } from "../../lib/dashboardView";
import type { DashboardTone } from "../../model/types";
import type { SummaryDetail } from "../../lib/dashboardView";

type SummaryCardsProps = {
  bedZoneDetail: SummaryDetail;
  houseDetail: SummaryDetail;
  physicalBedDetail: SummaryDetail;
  summary: DashboardSummary;
};

export function SummaryCards({
  bedZoneDetail,
  houseDetail,
  physicalBedDetail,
  summary,
}: SummaryCardsProps) {
  return (
    <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
      <SummaryCard
        detail={houseDetail}
        icon={Home}
        label="전체 동 수"
        value={`${summary.houseCount}`}
        unit="개"
      />
      <SummaryCard
        detail={physicalBedDetail}
        icon={Map}
        label="전체 다이 수"
        value={`${summary.physicalBedCount}`}
        unit="개"
      />
      <SummaryCard
        detail={bedZoneDetail}
        icon={SquareDashed}
        label="전체 논리 구역 수"
        value={`${summary.bedZoneCount}`}
        unit="개"
      />
      <SummaryCard
        change="+2.9%"
        icon={Flower2}
        label="전체 난 묶음 수"
        value={summary.orchidGroupCount.toLocaleString()}
        unit="분"
      />
      <SummaryCard
        accent="orange"
        change="+12"
        icon={CalendarDays}
        label="분갈이 예정"
        value={`${summary.repotDueCount}`}
        unit="분"
      />
      <SummaryCard
        accent="red"
        change="+5"
        icon={AlertTriangle}
        label="상태 이상"
        value={`${summary.warningCount}`}
        unit="분"
      />
    </section>
  );
}

function SummaryCard({
  accent = "green",
  change,
  detail,
  icon: Icon,
  label,
  unit,
  value,
}: {
  accent?: DashboardTone;
  change?: string;
  detail?: SummaryDetail;
  icon: LucideIcon;
  label: string;
  unit: string;
  value: string;
}) {
  const color = toneText(accent);

  return (
    <div className="min-h-20 rounded-lg border border-[#dfe5dc] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#435047]">{label}</p>
          <p className="mt-1 text-3xl font-bold text-[#17251b]">
            <span className={color}>{value}</span>
            <span className="ml-1 text-sm font-medium text-[#344138]">
              {unit}
            </span>
          </p>
        </div>
        <Icon
          className={`h-8 w-8 ${color}`}
          strokeWidth={1.8}
          aria-hidden="true"
        />
      </div>
      {detail ? (
        <div className="mt-2 flex justify-between gap-2 text-xs font-semibold">
          {detail.map(([name, count, tone]) => (
            <span key={name} className={toneText(tone)}>
              {name} {count}
            </span>
          ))}
        </div>
      ) : (
        <div className="mt-2 flex items-center justify-between text-xs font-semibold">
          <span className="text-[#5c6a60]">지난주 대비</span>
          <span className="text-[#159447]">▲ {change}</span>
        </div>
      )}
    </div>
  );
}
