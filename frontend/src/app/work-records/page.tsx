import { WorkRecordManager } from "@/features/work-record/WorkRecordRoutePage";

export const dynamic = "force-dynamic";

export default async function Page({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const resolvedSearchParams = await searchParams;

  return <WorkRecordManager resolvedSearchParams={resolvedSearchParams} />;
}
