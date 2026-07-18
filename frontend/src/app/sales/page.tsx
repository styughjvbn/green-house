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
      AUCTION: "/sales/auction",
      SETTLEMENT: "/sales/settlement",
      PARTNERS: "/sales/partners",
      SLIPS: "/sales/slips",
    }[tab ?? "SLIPS"] ?? "/sales/slips";

  redirect(appendSearchParams(path, resolvedSearchParams, ["tab"]));
}
