import {
  getAuctionLots,
  getAuctionSettlements,
  getAuctionTrackingSummary,
  getBusinessPartners,
  getSalesSlipPage,
} from "../api/salesApi";
import type { SalesTab } from "../model/types";
import { SalesPage } from "./SalesPage";

export async function SalesRoutePage({
  activeTab,
  createSlip = false,
}: {
  activeTab: SalesTab;
  createSlip?: boolean;
}) {
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
