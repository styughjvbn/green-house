import type {
  DashboardSummary,
  FarmStatusMapData,
} from "@/entities/farm/types";
import type {
  SalesAnalyticsData,
  WorkAnalyticsData,
} from "@/features/analytics";

export type DashboardPageProps = {
  mapData: FarmStatusMapData;
  salesAnalytics: SalesAnalyticsData;
  summary: DashboardSummary;
  workAnalytics: WorkAnalyticsData;
};

export type DashboardTone = "green" | "orange" | "red";
