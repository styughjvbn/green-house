import {
  getAuctionLots,
  getAuctionSettlements,
  getAuctionTrackingSummary,
  getBusinessPartners,
  getSalesSlipPage,
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
  const requestedCreateSlip = resolvedSearchParams?.createSlip;
  const activeTab = normalizeSalesTab(
    Array.isArray(requestedTab) ? requestedTab[0] : requestedTab,
  );
  const createSlip =
    (Array.isArray(requestedCreateSlip)
      ? requestedCreateSlip[0]
      : requestedCreateSlip) === "1";

  if (activeTab === "AUCTION") {
    const [auctionPage, auctionSummary] = await Promise.all([
      getAuctionLots(),
      getAuctionTrackingSummary(),
    ]);

    return (
      <SalesPage
        activeTab={activeTab}
        createSlip={createSlip}
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
        createSlip={createSlip}
        auctionSettlements={auctionSettlements}
      />
    );
  }

  if (activeTab === "PARTNERS") {
    const partners = await getBusinessPartners();

    return (
      <SalesPage
        activeTab={activeTab}
        createSlip={createSlip}
        partners={partners}
      />
    );
  }

  const [partners, salesSlips] = await Promise.all([
    getBusinessPartners(),
    getSalesSlipPage(),
  ]);

  return (
    <SalesPage
      activeTab={activeTab}
      createSlip={createSlip}
      partners={partners}
      salesSlipPage={salesSlips}
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
