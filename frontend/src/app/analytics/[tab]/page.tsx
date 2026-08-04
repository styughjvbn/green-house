import { notFound } from "next/navigation";
import { AnalyticsRoutePage } from "@/features/analytics/AnalyticsRoutePage";
import { isAnalyticsTab } from "@/shared/config/routes";

export const dynamic = "force-dynamic";

export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ tab: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { tab } = await params;
  if (!isAnalyticsTab(tab)) notFound();
  const resolvedSearchParams = await searchParams;

  return (
    <AnalyticsRoutePage
      activeTab={tab}
      resolvedSearchParams={resolvedSearchParams}
    />
  );
}
