import type {
  DashboardSummary,
  FarmStatusMapData,
  House,
  PartnerBalanceSummary,
  WorkRecord,
} from "@/entities/farm/types";
import { fetchApi } from "@/shared/api/client";
import type {
  AnalyticsTab,
  PartnerAnalyticsData,
  SalesAnalyticsData,
  WorkAnalyticsData,
} from "../model/types";

export async function getAnalyticsData(tab: AnalyticsTab) {
  const [summary, mapData] = await Promise.all([
    fetchApi<DashboardSummary>("/dashboard/summary"),
    fetchApi<FarmStatusMapData>("/farm-status/map"),
  ]);

  if (tab === "SPACE") {
    const houses = await fetchApi<House[]>("/houses");
    return createAnalyticsData({
      houses,
      mapData,
      summary,
    });
  }

  if (tab === "WORK") {
    const workAnalytics = await fetchApi<WorkAnalyticsData>("/analytics/work");
    return createAnalyticsData({
      mapData,
      summary,
      workAnalytics,
    });
  }

  const salesAnalytics = await fetchApi<SalesAnalyticsData>("/analytics/sales");

  if (tab === "CUSTOMER") {
    const partnerAnalytics = await fetchApi<PartnerAnalyticsData>(
      "/analytics/partners",
    );
    return createAnalyticsData({
      mapData,
      partnerAnalytics,
      salesAnalytics,
      summary,
    });
  }

  return createAnalyticsData({
    mapData,
    salesAnalytics,
    summary,
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
  workRecords = [],
}: {
  houses?: House[];
  mapData: FarmStatusMapData;
  partnerBalances?: PartnerBalanceSummary[];
  partnerAnalytics?: PartnerAnalyticsData | null;
  salesAnalytics?: SalesAnalyticsData | null;
  summary: DashboardSummary;
  workAnalytics?: WorkAnalyticsData | null;
  workRecords?: WorkRecord[];
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
    workRecords,
  };
}
