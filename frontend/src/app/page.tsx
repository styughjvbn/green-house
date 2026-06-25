import { DashboardPage, getDashboardData } from "@/features/dashboard";

export const dynamic = "force-dynamic";

export default async function Page() {
  const dashboardData = await getDashboardData();

  return <DashboardPage {...dashboardData} />;
}
