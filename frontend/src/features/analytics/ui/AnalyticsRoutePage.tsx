import { getAnalyticsData } from "../api/analyticsApi";
import type { AnalyticsTab } from "../model/types";
import { AnalyticsPage } from "./AnalyticsPage";

export async function AnalyticsRoutePage({
  activeTab,
}: {
  activeTab: AnalyticsTab;
}) {
  const data = await getAnalyticsData(activeTab);
  return <AnalyticsPage activeTab={activeTab} {...data} />;
}
