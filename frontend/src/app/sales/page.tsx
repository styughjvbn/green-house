import {
  getAuctionLots,
  getAuctionSettlements,
  getAuctionTrackingSummary,
  getBusinessPartners,
  getSalesSlips,
  SalesPage,
} from "@/features/sales";
import type { SalesTab } from "@/features/sales/model/types";

export const dynamic = "force-dynamic";

export default async function Page({
  searchParams,
}: {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
  const resolvedSearchParams = searchParams ? await searchParams : undefined;
  const requestedTab = resolvedSearchParams?.tab;
  const activeTab = normalizeSalesTab(
    Array.isArray(requestedTab) ? requestedTab[0] : requestedTab,
  );

  if (activeTab === "AUCTION") {
    const [auctionPage, auctionSummary] = await Promise.all([
      getAuctionLots(),
      getAuctionTrackingSummary(),
    ]);

    return (
      <SalesPage
        activeTab={activeTab}
        auctionPage={auctionPage}
        auctionSummary={auctionSummary}
      />
    );
  }

  if (activeTab === "SETTLEMENT") {
    const auctionSettlements = await getAuctionSettlements();

    return (
      <SalesPage
        activeTab={activeTab}
        auctionSettlements={auctionSettlements}
      />
    );
  }

  if (activeTab === "PARTNERS") {
    const partners = await getBusinessPartners();

    return <SalesPage activeTab={activeTab} partners={partners} />;
  }

  const [partners, salesSlips] = await Promise.all([
    getBusinessPartners(),
    getSalesSlips(),
  ]);

  return (
    <SalesPage
      activeTab={activeTab}
      partners={partners}
      salesSlips={salesSlips}
    />
  );
}

function normalizeSalesTab(value?: string): SalesTab {
  if (
    value === "SLIPS" ||
    value === "AUCTION" ||
    value === "SETTLEMENT" ||
    value === "PARTNERS"
  ) {
    return value;
  }

  return "SLIPS";
}
