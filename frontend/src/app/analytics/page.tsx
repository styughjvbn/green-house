import { AnalyticsPage, getAnalyticsData } from "@/features/analytics";

export const dynamic = "force-dynamic";

export default async function Page() {
  const data = await getAnalyticsData();

  return <AnalyticsPage {...data} />;
}
