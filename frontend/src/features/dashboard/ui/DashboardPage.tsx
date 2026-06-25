import Link from "next/link";
import type { ReactNode } from "react";
import {
  AlertTriangle,
  CalendarDays,
  ClipboardEdit,
  FileText,
  Flower2,
  Home,
  Map,
  PlusCircle,
  Printer,
  Search,
  SquareDashed,
  type LucideIcon,
} from "lucide-react";
import type {
  DashboardSummary,
  FarmStatusMapData,
  HouseStatusSummary,
  SalesSlip,
  WorkRecord,
} from "@/entities/farm/types";

type DashboardPageProps = {
  mapData: FarmStatusMapData;
  salesSlips: SalesSlip[];
  summary: DashboardSummary;
  workRecords: WorkRecord[];
};

export function DashboardPage({
  mapData,
  salesSlips,
  summary,
  workRecords,
}: DashboardPageProps) {
  const recentWorkRecords = workRecords.slice(0, 5);
  const recentSalesSlips = salesSlips.slice(0, 5);
  const salesTotal = salesSlips.reduce((sum, slip) => sum + slip.totalAmount, 0);
  const unpaidCount = salesSlips.filter((slip) => slip.paymentStatus === "미입금").length;
  const warningHouses = mapData.houses.filter((house) => house.warningCount > 0);

  return (
    <main className="space-y-4">
      <section>
        <h1 className="text-2xl font-bold text-[#17251b]">
          안녕하세요, 관리자님! <span className="text-[#39b85b]">🌱</span>
        </h1>
        <p className="mt-1 text-sm text-[#5c6a60]">
          오늘도 건강한 난 농장을 만들어가세요.
        </p>
      </section>

      <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
        <SummaryCard
          detail={[
            ["정상", Math.max(summary.houseCount - warningHouses.length, 0), "green"],
            ["주의", warningHouses.length, "orange"],
            ["이상", summary.warningCount, "red"],
          ]}
          icon={Home}
          label="전체 동 수"
          value={`${summary.houseCount}`}
          unit="개"
        />
        <SummaryCard
          detail={[
            ["정상", Math.max(summary.physicalBedCount - summary.warningCount, 0), "green"],
            ["주의", summary.warningCount, "orange"],
            ["이상", 0, "red"],
          ]}
          icon={Map}
          label="전체 물리 배드 수"
          value={`${summary.physicalBedCount}`}
          unit="개"
        />
        <SummaryCard
          detail={[
            ["정상", Math.max(summary.bedZoneCount - summary.warningCount, 0), "green"],
            ["주의", summary.warningCount, "orange"],
            ["이상", 0, "red"],
          ]}
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

      <section className="grid gap-4 xl:grid-cols-[1fr_1.15fr_1.1fr]">
        <TodayChecklist warningCount={summary.warningCount} repotDueCount={summary.repotDueCount} />
        <HouseStatusGrid houses={mapData.houses} />
        <RecentWorkSummary records={recentWorkRecords} />
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.05fr_0.95fr_1.25fr]">
        <IssueTable houses={warningHouses} />
        <QuickActions />
        <SalesSummary salesSlips={recentSalesSlips} totalAmount={salesTotal} unpaidCount={unpaidCount} />
      </section>
    </main>
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
  accent?: "green" | "orange" | "red";
  change?: string;
  detail?: Array<[string, number, "green" | "orange" | "red"]>;
  icon: LucideIcon;
  label: string;
  unit: string;
  value: string;
}) {
  const color = {
    green: "text-[#159447]",
    orange: "text-[#f59e0b]",
    red: "text-[#e52d2d]",
  }[accent];

  return (
    <div className="min-h-32 rounded-lg border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#435047]">{label}</p>
          <p className="mt-3 text-3xl font-bold text-[#17251b]">
            <span className={color}>{value}</span>
            <span className="ml-1 text-sm font-medium text-[#344138]">{unit}</span>
          </p>
        </div>
        <Icon className={`h-8 w-8 ${color}`} strokeWidth={1.8} aria-hidden="true" />
      </div>
      {detail ? (
        <div className="mt-5 flex justify-between gap-2 text-xs font-semibold">
          {detail.map(([name, count, tone]) => (
            <span key={name} className={toneText(tone)}>
              {name} {count}
            </span>
          ))}
        </div>
      ) : (
        <div className="mt-5 flex items-center justify-between text-xs font-semibold">
          <span className="text-[#5c6a60]">지난주 대비</span>
          <span className="text-[#159447]">▲ {change}</span>
        </div>
      )}
    </div>
  );
}

function TodayChecklist({
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
    <Panel title="오늘 확인 필요">
      <div className="space-y-2">
        {items.map(([label, count, unit, tone]) => (
          <div key={label} className="flex items-center gap-3 rounded-md border border-[#edf0ec] bg-white px-3 py-2.5">
            <span className={`flex h-6 w-6 items-center justify-center rounded-full border text-sm ${toneBorder(tone)}`}>!</span>
            <span className="min-w-0 flex-1 text-sm font-semibold text-[#344138]">{label}</span>
            <span className="text-base font-bold text-[#17251b]">{count}</span>
            <span className="text-sm text-[#5c6a60]">{unit}</span>
            <Link className="rounded-md border border-[#dfe5dc] px-3 py-1.5 text-xs font-semibold" href="/farm-status">
              확인하기
            </Link>
          </div>
        ))}
      </div>
    </Panel>
  );
}

function HouseStatusGrid({ houses }: { houses: HouseStatusSummary[] }) {
  return (
    <Panel
      title="15동 상태 한눈에 보기"
      action={
        <div className="flex gap-4 text-xs font-semibold">
          <Legend tone="green" label="정상" />
          <Legend tone="orange" label="주의" />
          <Legend tone="red" label="이상" />
        </div>
      }
    >
      <div className="grid grid-cols-5 gap-3">
        {houses.map((house) => {
          const tone = house.warningCount > 1 ? "red" : house.warningCount > 0 ? "orange" : "green";

          return (
            <Link
              key={house.houseId}
              className={`relative flex h-16 items-center justify-center rounded-t-2xl border text-lg font-bold ${houseToneClass(tone)}`}
              href={`/orchid-groups?houseId=${house.houseId}`}
            >
              <span className={`absolute top-2 h-2 w-2 rounded-full ${dotClass(tone)}`} />
              {house.houseNumber}동
            </Link>
          );
        })}
      </div>
      <Link className="mt-4 flex h-10 items-center justify-center rounded-md border border-[#dfe5dc] text-sm font-semibold text-[#344138]" href="/farm-status">
        농장 현황 전체 보기
      </Link>
    </Panel>
  );
}

function RecentWorkSummary({ records }: { records: WorkRecord[] }) {
  return (
    <Panel title="최근 작업 요약">
      <div className="divide-y divide-[#edf0ec]">
        {records.map((record) => (
          <Link key={record.id} className="grid grid-cols-[1fr_110px_1fr_16px] items-center gap-3 py-3 text-sm" href="/work-records">
            <span className="font-semibold text-[#344138]">{record.workType}</span>
            <span className="text-[#5c6a60]">{record.workDate}</span>
            <span className="truncate text-[#5c6a60]">{record.memo ?? record.materialName ?? "농장 전체"}</span>
            <span className="text-[#8a968f]">›</span>
          </Link>
        ))}
        {records.length === 0 ? <EmptyText text="최근 작업 이력이 없습니다." /> : null}
      </div>
      <Link className="mt-3 flex h-10 items-center justify-center rounded-md border border-[#dfe5dc] text-sm font-semibold text-[#344138]" href="/work-records">
        작업 이력 전체 보기
      </Link>
    </Panel>
  );
}

function IssueTable({ houses }: { houses: HouseStatusSummary[] }) {
  const rows = houses.slice(0, 5);

  return (
    <Panel
      title="상태 이상 / 주의 목록"
      action={<Link className="rounded-md border border-[#dfe5dc] px-3 py-1.5 text-xs font-semibold" href="/farm-status">전체 보기</Link>}
    >
      <table className="w-full text-sm">
        <thead className="border-y border-[#edf0ec] text-left text-[#6a766e]">
          <tr>
            <th className="py-2 font-semibold">위치</th>
            <th className="py-2 font-semibold">내용</th>
            <th className="py-2 font-semibold">상태</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((house) => (
            <tr key={house.houseId} className="border-b border-[#edf0ec]">
              <td className="py-2 font-semibold text-[#246df2]">{house.houseNumber}동</td>
              <td className="py-2">확인 필요 {house.warningCount}건</td>
              <td className="py-2">
                <span className="rounded-md bg-[#fff1d6] px-2 py-1 text-xs font-bold text-[#d88400]">주의</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length === 0 ? <EmptyText text="상태 이상 또는 주의 항목이 없습니다." /> : null}
    </Panel>
  );
}

function QuickActions() {
  const actions: Array<[string, string, LucideIcon, string]> = [
    ["농장 현황 보기", "/farm-status", Map, "green"],
    ["난 묶음 추가", "/orchid-groups", PlusCircle, "green"],
    ["작업 기록 추가", "/work-records", ClipboardEdit, "green"],
    ["판매 전표 등록", "/sales", FileText, "green"],
    ["출력하기", "/print", Printer, "blue"],
    ["검색하기", "/farm-status", Search, "blue"],
  ];

  return (
    <Panel title="빠른 작업">
      <div className="grid grid-cols-3 gap-3">
        {actions.map(([label, href, Icon, tone]) => (
          <Link key={label} className="flex min-h-24 flex-col items-center justify-center gap-2 rounded-md border border-[#dfe5dc] bg-white text-sm font-semibold text-[#344138] hover:bg-[#eef7ec]" href={href}>
            <Icon className={`h-8 w-8 ${tone === "blue" ? "text-[#246df2]" : "text-[#159447]"}`} strokeWidth={1.8} aria-hidden="true" />
            {label}
          </Link>
        ))}
      </div>
    </Panel>
  );
}

function SalesSummary({
  salesSlips,
  totalAmount,
  unpaidCount,
}: {
  salesSlips: SalesSlip[];
  totalAmount: number;
  unpaidCount: number;
}) {
  return (
    <Panel
      title="판매 요약"
      action={<span className="rounded-md border border-[#dfe5dc] px-3 py-1.5 text-xs font-semibold">이번 달</span>}
    >
      <div className="grid grid-cols-3 rounded-md border border-[#edf0ec] text-sm">
        <Metric label="전표 수" value={`${salesSlips.length}건`} />
        <Metric label="판매 금액" value={`${totalAmount.toLocaleString()}원`} />
        <Metric label="미입금 전표" value={`${unpaidCount}건`} warning />
      </div>
      <div className="mt-3 divide-y divide-[#edf0ec]">
        {salesSlips.map((slip) => (
          <Link key={slip.id} className="grid grid-cols-[1fr_1fr_90px_56px] items-center gap-2 py-2 text-sm" href="/sales">
            <span className="font-semibold text-[#344138]">{slip.slipNumber}</span>
            <span className="truncate text-[#5c6a60]">{slip.customer.name}</span>
            <span className="text-right font-bold">{slip.totalAmount.toLocaleString()}원</span>
            <span className={`rounded-md px-2 py-1 text-center text-xs font-bold ${slip.paymentStatus === "미입금" ? "bg-[#fff1d6] text-[#d88400]" : "bg-[#e7f7e8] text-[#16853b]"}`}>
              {slip.paymentStatus === "미입금" ? "미입금" : "입금"}
            </span>
          </Link>
        ))}
      </div>
    </Panel>
  );
}

function Panel({
  action,
  children,
  title,
}: {
  action?: ReactNode;
  children: ReactNode;
  title: string;
}) {
  return (
    <section className="rounded-lg border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="text-lg font-bold text-[#17251b]">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

function Metric({ label, value, warning = false }: { label: string; value: string; warning?: boolean }) {
  return (
    <div className="border-r border-[#edf0ec] p-3 last:border-r-0">
      <p className="text-xs text-[#6a766e]">{label}</p>
      <p className={`mt-1 font-bold ${warning ? "text-[#e52d2d]" : "text-[#17251b]"}`}>{value}</p>
    </div>
  );
}

function Legend({ label, tone }: { label: string; tone: "green" | "orange" | "red" }) {
  return (
    <span className="inline-flex items-center gap-1">
      <span className={`h-2 w-2 rounded-full ${dotClass(tone)}`} />
      {label}
    </span>
  );
}

function EmptyText({ text }: { text: string }) {
  return <p className="py-6 text-center text-sm text-[#5c6a60]">{text}</p>;
}

function toneText(tone: "green" | "orange" | "red") {
  return {
    green: "text-[#159447]",
    orange: "text-[#f59e0b]",
    red: "text-[#e52d2d]",
  }[tone];
}

function toneBorder(tone: "blue" | "orange" | "red") {
  return {
    blue: "border-[#8dbdff] text-[#246df2]",
    orange: "border-[#ffc66d] text-[#f59e0b]",
    red: "border-[#ff9c9c] text-[#e52d2d]",
  }[tone];
}

function dotClass(tone: "green" | "orange" | "red") {
  return {
    green: "bg-[#159447]",
    orange: "bg-[#f59e0b]",
    red: "bg-[#e52d2d]",
  }[tone];
}

function houseToneClass(tone: "green" | "orange" | "red") {
  return {
    green: "border-[#b8dbc0] bg-[#eef8ef] text-[#0d6d31]",
    orange: "border-[#f5d383] bg-[#fff8e8] text-[#f59e0b]",
    red: "border-[#ffb1b1] bg-[#fff0f0] text-[#e52d2d]",
  }[tone];
}
