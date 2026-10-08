import { notFound } from "next/navigation";
import { SalesV2RoutePage } from "@/features/sales";
import { isSalesV2Tab } from "@/shared/config/routes";

export const dynamic = "force-dynamic";
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ tab: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { tab } = await params;
  if (!isSalesV2Tab(tab)) notFound();
  return (
    <SalesV2RoutePage
      activeTab={tab}
      resolvedSearchParams={await searchParams}
    />
  );
}
