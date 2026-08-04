import type {
  DashboardSummary,
  FarmStatusMapData,
  House,
  PartnerBalanceSummary,
} from "@/entities/farm/types";
import { fetchApi } from "@/shared/api/client";
import type {
  AnalyticsTab,
  PartnerAnalyticsData,
  SalesAnalyticsData,
  WorkAnalyticsData,
} from "../model/types";

export async function getAnalyticsData(
  tab: AnalyticsTab,
  dateRange: { dateFrom: string; dateTo: string },
) {
  const query = new URLSearchParams({
    from: dateRange.dateFrom,
    to: dateRange.dateTo,
  }).toString();
  const [summary, mapData, salesAnalytics] = await Promise.all([
    fetchApi<DashboardSummary>("/dashboard/summary"),
    fetchApi<FarmStatusMapData>("/farm-status/map"),
    fetchApi<SalesAnalyticsData>(`/analytics/sales?${query}`),
  ]);

  if (tab === "space") {
    const houses = await fetchApi<House[]>("/houses");
    return createAnalyticsData({
      houses,
      mapData,
      salesAnalytics,
      summary,
      dateRange,
    });
  }

  if (tab === "work") {
    const workAnalytics = await fetchApi<WorkAnalyticsData>(
      `/analytics/work?${query}`,
    );
    return createAnalyticsData({
      mapData,
      salesAnalytics,
      summary,
      workAnalytics,
      dateRange,
    });
  }

  if (tab === "customer") {
    const partnerAnalytics = await fetchApi<PartnerAnalyticsData>(
      `/analytics/partners?${query}`,
    );
    return createAnalyticsData({
      mapData,
      partnerAnalytics,
      salesAnalytics,
      summary,
      dateRange,
    });
  }

  return createAnalyticsData({
    mapData,
    salesAnalytics,
    summary,
    dateRange,
  });
}

function createAnalyticsData({
  houses = [],
  mapData,
  partnerBalances = [],
  partnerAnalytics = null,
  salesAnalytics = null,
  summary,
  workAnalytics = null,
  dateRange,
}: {
  houses?: House[];
  mapData: FarmStatusMapData;
  partnerBalances?: PartnerBalanceSummary[];
  partnerAnalytics?: PartnerAnalyticsData | null;
  salesAnalytics?: SalesAnalyticsData | null;
  summary: DashboardSummary;
  workAnalytics?: WorkAnalyticsData | null;
  dateRange: { dateFrom: string; dateTo: string };
}) {
  return {
    businessPartners: [],
    houses,
    mapData,
    partnerBalances,
    partnerAnalytics,
    salesAnalytics,
    salesSlips: salesAnalytics?.recentSlips ?? [],
    summary,
    workAnalytics,
    dateRange,
  };
}
