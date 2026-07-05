import type {
  AuctionLotPage,
  AuctionTrackingSummary,
  Customer,
  SalesSlip,
} from "@/entities/farm/types";
import { SalesManager } from "./SalesManager";

type SalesPageProps = {
  customers: Customer[];
  salesSlips: SalesSlip[];
  auctionPage: AuctionLotPage;
  auctionSummary: AuctionTrackingSummary;
};

export function SalesPage({
  customers,
  salesSlips,
  auctionPage,
  auctionSummary,
}: SalesPageProps) {
  return (
    <main className="space-y-5">
      <SalesManager
        initialCustomers={customers}
        initialSalesSlips={salesSlips}
        initialAuctionPage={auctionPage}
        initialAuctionSummary={auctionSummary}
      />
    </main>
  );
}
