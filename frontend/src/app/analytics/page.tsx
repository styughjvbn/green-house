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
      SALES: "/analytics/sales",
      VARIETY: "/analytics/variety",
      CUSTOMER: "/analytics/customer",
      SPACE: "/analytics/space",
      WORK: "/analytics/work",
    }[tab ?? "SALES"] ?? "/analytics/sales";

  redirect(appendSearchParams(path, resolvedSearchParams, ["tab"]));
}
