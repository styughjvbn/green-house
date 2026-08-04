import { getAnalyticsData } from "./api/analyticsApi";
import { readAnalyticsDateRange } from "./lib/analyticsDateRange";
import type { AnalyticsTab } from "./model/types";
import { AnalyticsPage } from "./ui/AnalyticsPage";

export async function AnalyticsRoutePage({
  activeTab,
  resolvedSearchParams,
}: {
  activeTab: AnalyticsTab;
  resolvedSearchParams: Record<string, string | string[] | undefined>;
}) {
  const dateRange = readAnalyticsDateRange(
    readFirstValue(resolvedSearchParams.from),
    readFirstValue(resolvedSearchParams.to),
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
