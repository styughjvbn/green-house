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
  partners?: BusinessPartner[];
  salesSlips?: SalesSlip[];
  auctionPage?: AuctionLotPage;
  auctionSummary?: AuctionTrackingSummary;
  auctionSettlements?: AuctionSettlement[];
};

export function SalesPage({
  activeTab,
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
        initialBusinessPartners={partners}
        initialSalesSlips={salesSlips}
        initialAuctionPage={auctionPage}
        initialAuctionSummary={auctionSummary}
        initialAuctionSettlements={auctionSettlements}
      />
    </main>
  );
}
