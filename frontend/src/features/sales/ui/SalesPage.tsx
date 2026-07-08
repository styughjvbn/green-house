import type {
  AuctionLotPage,
  AuctionSettlement,
  AuctionTrackingSummary,
  BusinessPartner,
  SalesSlip,
} from "@/entities/farm/types";
import type { SalesTab } from "../model/types";
import { SalesManager } from "./SalesManager";

type SalesPageProps = {
  activeTab: SalesTab;
  createSlip?: boolean;
  partners?: BusinessPartner[];
  salesSlips?: SalesSlip[];
  auctionPage?: AuctionLotPage;
  auctionSummary?: AuctionTrackingSummary;
  auctionSettlements?: AuctionSettlement[];
};

export function SalesPage({
  activeTab,
  createSlip = false,
  partners,
  salesSlips,
  auctionPage,
  auctionSummary,
  auctionSettlements,
}: SalesPageProps) {
  return (
    <main className="space-y-5">
      <SalesManager
        activeTab={activeTab}
        initialShowCreateSlip={createSlip}
        initialBusinessPartners={partners}
        initialSalesSlips={salesSlips}
        initialAuctionPage={auctionPage}
        initialAuctionSummary={auctionSummary}
        initialAuctionSettlements={auctionSettlements}
      />
    </main>
  );
}
