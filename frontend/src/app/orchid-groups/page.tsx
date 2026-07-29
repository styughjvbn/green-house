import { OrchidManagementRoutePage } from "@/features/orchid-management";

export const dynamic = "force-dynamic";

export default async function Page({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const resolvedSearchParams = await searchParams;

  return (
    <OrchidManagementRoutePage resolvedSearchParams={resolvedSearchParams} />
  );
}
