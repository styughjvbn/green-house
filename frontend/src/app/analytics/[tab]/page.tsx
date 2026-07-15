import { notFound } from "next/navigation";
import type { AnalyticsTab } from "@/features/analytics/model/types";
import { AnalyticsRoutePage } from "@/features/analytics/ui/AnalyticsRoutePage";

export const dynamic = "force-dynamic";

const ANALYTICS_TABS: Record<string, AnalyticsTab> = {
  sales: "SALES",
  variety: "VARIETY",
  customer: "CUSTOMER",
  space: "SPACE",
  work: "WORK",
};

export default async function Page({
  params,
}: {
  params: Promise<{ tab: string }>;
}) {
  const { tab } = await params;
  const activeTab = ANALYTICS_TABS[tab];
  if (!activeTab) notFound();

  return <AnalyticsRoutePage activeTab={activeTab} />;
}
