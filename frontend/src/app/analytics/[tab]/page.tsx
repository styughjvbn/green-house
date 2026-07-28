import { notFound } from "next/navigation";
import { AnalyticsRoutePage } from "@/features/analytics/AnalyticsRoutePage";
import { isAnalyticsTab } from "@/shared/config/routes";

export const dynamic = "force-dynamic";

export default async function Page({
  params,
}: {
  params: Promise<{ tab: string }>;
}) {
  const { tab } = await params;
  if (!isAnalyticsTab(tab)) notFound();

  return <AnalyticsRoutePage activeTab={tab} />;
}
