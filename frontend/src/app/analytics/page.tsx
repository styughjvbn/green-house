import { AnalyticsPage, getAnalyticsData } from "@/features/analytics";
import type { AnalyticsTab } from "@/features/analytics/model/types";

export const dynamic = "force-dynamic";

export default async function Page({
  searchParams,
}: {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
  const resolvedSearchParams = searchParams ? await searchParams : undefined;
  const requestedTab = resolvedSearchParams?.tab;
  const activeTab = normalizeAnalyticsTab(
    Array.isArray(requestedTab) ? requestedTab[0] : requestedTab,
  );
  const data = await getAnalyticsData(activeTab);

  return <AnalyticsPage activeTab={activeTab} {...data} />;
}

function normalizeAnalyticsTab(value?: string): AnalyticsTab {
  if (
    value === "SALES" ||
    value === "VARIETY" ||
    value === "CUSTOMER" ||
    value === "SPACE" ||
    value === "WORK"
  ) {
    return value;
  }

  return "SALES";
}
