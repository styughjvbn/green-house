import { createDashboardViewModel } from "../lib/dashboardView";
import type { DashboardPageProps } from "../model/types";
import { HouseStatusGrid } from "./components/HouseStatusGrid";
import { IssueTable } from "./components/IssueTable";
import { QuickActions } from "./components/QuickActions";
import { RecentWorkSummary } from "./components/RecentWorkSummary";
import { SalesSummary } from "./components/SalesSummary";
import { SummaryCards } from "./components/SummaryCards";
import { TodayChecklist } from "./components/TodayChecklist";

export function DashboardPage(props: DashboardPageProps) {
  const {
    recentSalesSlips,
    recentWorkRecords,
    salesTotal,
    summaryCards,
    unpaidCount,
    warningHouses,
  } = createDashboardViewModel(props);

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

      <SummaryCards
        bedZoneDetail={summaryCards.bedZones}
        houseDetail={summaryCards.houses}
        physicalBedDetail={summaryCards.physicalBeds}
        summary={props.summary}
      />

      <section className="grid gap-4 xl:grid-cols-[1fr_1.15fr_1.1fr]">
        <TodayChecklist
          repotDueCount={props.summary.repotDueCount}
          warningCount={props.summary.warningCount}
        />
        <HouseStatusGrid houses={props.mapData.houses} />
        <RecentWorkSummary records={recentWorkRecords} />
      </section>

      <section className="grid gap-4 xl:grid-cols-[1.05fr_0.95fr_1.25fr]">
        <IssueTable houses={warningHouses} />
        <QuickActions />
        <SalesSummary
          salesSlips={recentSalesSlips}
          totalAmount={salesTotal}
          unpaidCount={unpaidCount}
        />
      </section>
    </main>
  );
}
