import { getPrintableSalesSlips, PrintPage } from "@/features/print";

export const dynamic = "force-dynamic";

export default async function Page({
  searchParams,
}: {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
  const resolvedSearchParams = searchParams ? await searchParams : undefined;
  const page = parsePositiveInt(resolvedSearchParams?.page, 0);
  const size = parsePositiveInt(resolvedSearchParams?.size, 10);
  const salesSlipPage = await getPrintableSalesSlips(page, size);

  return <PrintPage salesSlipPage={salesSlipPage} />;
}

function parsePositiveInt(
  value: string | string[] | undefined,
  fallback: number,
) {
  const source = Array.isArray(value) ? value[0] : value;
  const parsed = Number(source);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback;
}
