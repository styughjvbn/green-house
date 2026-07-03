import type {
  AuctionLot,
  AuctionTrackingSummary,
  Customer,
  SalesSlip,
} from "@/entities/farm/types";
import { SalesManager } from "./SalesManager";

type SalesPageProps = {
  customers: Customer[];
  salesSlips: SalesSlip[];
  auctionLots: AuctionLot[];
  auctionSummary: AuctionTrackingSummary;
};

export function SalesPage({
  customers,
  salesSlips,
  auctionLots,
  auctionSummary,
}: SalesPageProps) {
  return (
    <main className="space-y-5">
      <SalesManager
        initialCustomers={customers}
        initialSalesSlips={salesSlips}
        initialAuctionLots={auctionLots}
        initialAuctionSummary={auctionSummary}
      />
    </main>
  );
}
