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
