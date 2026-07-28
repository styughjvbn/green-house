import { getDashboardData } from "./api/dashboardApi";
import { createDashboardViewModel } from "./lib/dashboardView";
import { HouseStatusGrid } from "./ui/components/HouseStatusGrid";
import { IssueTable } from "./ui/components/IssueTable";
import { QuickActions } from "./ui/components/QuickActions";
import { RecentWorkSummary } from "./ui/components/RecentWorkSummary";
import { SalesSummary } from "./ui/components/SalesSummary";
import { SummaryCards } from "./ui/components/SummaryCards";
import { TodayChecklist } from "./ui/components/TodayChecklist";

export async function DashboardPage() {
  const dashboardData = await getDashboardData();
  const {
    recentSalesSlips,
    recentWorkRecords,
    salesTotal,
    summaryCards,
    unpaidCount,
    warningHouses,
  } = createDashboardViewModel(dashboardData);

  return (
    <main className="space-y-4">
      <SummaryCards
        bedZoneDetail={summaryCards.bedZones}
        houseDetail={summaryCards.houses}
        physicalBedDetail={summaryCards.physicalBeds}
        summary={dashboardData.summary}
      />

      <section className="grid gap-4 xl:grid-cols-[1fr_1.15fr_1.1fr]">
        <TodayChecklist
          repotDueCount={dashboardData.summary.repotDueCount}
          warningCount={dashboardData.summary.warningCount}
        />
        <HouseStatusGrid houses={dashboardData.mapData.houses} />
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
