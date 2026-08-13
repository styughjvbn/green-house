import { getAnalyticsData } from "./api/analyticsApi";
import { readAnalyticsDateRange } from "./lib/analyticsDateRange";
import type { AnalyticsTab } from "./model/types";
import { AnalyticsPage } from "./ui/AnalyticsPage";
import { getRuntimeContext } from "@/shared/api/runtimeContext";

export async function AnalyticsRoutePage({
  activeTab,
  resolvedSearchParams,
}: {
  activeTab: AnalyticsTab;
  resolvedSearchParams: Record<string, string | string[] | undefined>;
}) {
  const { businessDate } = await getRuntimeContext();
  const dateRange = readAnalyticsDateRange(
    readFirstValue(resolvedSearchParams.from),
    readFirstValue(resolvedSearchParams.to),
    businessDate,
  );
  const data = await getAnalyticsData(activeTab, dateRange);
  return (
    <AnalyticsPage
      key={`${dateRange.dateFrom}:${dateRange.dateTo}`}
      activeTab={activeTab}
      {...data}
    />
  );
}

function readFirstValue(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}
