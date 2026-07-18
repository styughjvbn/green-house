import { redirect } from "next/navigation";
import { appendSearchParams } from "@/shared/lib/route";

export default async function Page({
  searchParams,
}: {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
  const resolvedSearchParams = searchParams ? await searchParams : undefined;
  const requestedTab = resolvedSearchParams?.tab;
  const tab = Array.isArray(requestedTab) ? requestedTab[0] : requestedTab;
  const path =
    {
      LIST: "/work-records/list",
      CALENDAR: "/work-records/calendar",
      HISTORY: "/work-records/history",
    }[tab ?? "LIST"] ?? "/work-records/list";

  redirect(appendSearchParams(path, resolvedSearchParams, ["tab"]));
}
