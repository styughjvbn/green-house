import type {
  DashboardSummary,
  FarmStatusMapData,
} from "@/entities/farm/types";
import type {
  SalesAnalyticsData,
  WorkAnalyticsData,
} from "@/features/analytics/model/types";
import { fetchApi } from "@/shared/api/client";

export async function getDashboardData() {
  const [summary, mapData, salesAnalytics, workAnalytics] = await Promise.all([
    fetchApi<DashboardSummary>("/dashboard/summary"),
    fetchApi<FarmStatusMapData>("/farm-status/map"),
    fetchApi<SalesAnalyticsData>("/analytics/sales"),
    fetchApi<WorkAnalyticsData>("/analytics/work"),
  ]);

  return {
    mapData,
    salesAnalytics,
    summary,
    workAnalytics,
  };
}
