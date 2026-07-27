import type { ServerSearchParams } from "@/features/work-record/lib/workRecordUrlState";
import { WorkRecordManager } from "@/features/work-record/ui/WorkRecordManager";

export const dynamic = "force-dynamic";

export default function Page({
  searchParams,
}: {
  searchParams: Promise<ServerSearchParams>;
}) {
  return (
    <main className="h-full min-h-0">
      <WorkRecordManager searchParams={searchParams} />
    </main>
  );
}
