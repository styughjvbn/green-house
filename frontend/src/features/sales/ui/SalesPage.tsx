import type {
  AuctionLotPage,
  AuctionSettlement,
  AuctionTrackingSummary,
  BusinessPartner,
} from "@/entities/farm/types";
import type { SalesSlipPage, SalesTab } from "../model/types";
import { SalesManager } from "./SalesManager";

type SalesPageProps = {
  activeTab: SalesTab;
  createSlip?: boolean;
  partners?: BusinessPartner[];
  salesSlipPage?: SalesSlipPage;
  auctionPage?: AuctionLotPage;
  auctionSummary?: AuctionTrackingSummary;
  auctionSettlements?: AuctionSettlement[];
};

export function SalesPage({
  activeTab,
  createSlip = false,
  partners,
  salesSlipPage,
  auctionPage,
  auctionSummary,
  auctionSettlements,
}: SalesPageProps) {
  return (
    <main className="h-full min-h-0">
      <SalesManager
        activeTab={activeTab}
        initialShowCreateSlip={createSlip}
        initialBusinessPartners={partners}
        initialSalesSlipPage={salesSlipPage}
        initialAuctionPage={auctionPage}
        initialAuctionSummary={auctionSummary}
        initialAuctionSettlements={auctionSettlements}
      />
    </main>
  );
}
