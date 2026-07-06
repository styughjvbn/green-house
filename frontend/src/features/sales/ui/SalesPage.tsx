import type {
  AuctionLotPage,
  AuctionTrackingSummary,
  AuctionSettlement,
  BusinessPartner,
  SalesSlip,
} from "@/entities/farm/types";
import { SalesManager } from "./SalesManager";

type SalesPageProps = {
  partners: BusinessPartner[];
  salesSlips: SalesSlip[];
  auctionPage: AuctionLotPage;
  auctionSummary: AuctionTrackingSummary;
  auctionSettlements: AuctionSettlement[];
};

export function SalesPage({
  partners,
  salesSlips,
  auctionPage,
  auctionSummary,
  auctionSettlements,
}: SalesPageProps) {
  return (
    <main className="space-y-5">
      <SalesManager
        initialBusinessPartners={partners}
        initialSalesSlips={salesSlips}
        initialAuctionPage={auctionPage}
        initialAuctionSummary={auctionSummary}
        initialAuctionSettlements={auctionSettlements}
      />
    </main>
  );
}
