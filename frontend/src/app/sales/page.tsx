import {
  getAuctionLots,
  getAuctionTrackingSummary,
  getCustomers,
  getSalesSlips,
  SalesPage,
} from "@/features/sales";

export const dynamic = "force-dynamic";

export default async function Page() {
  const [customers, salesSlips, auctionLots, auctionSummary] =
    await Promise.all([
      getCustomers(),
      getSalesSlips(),
      getAuctionLots(),
      getAuctionTrackingSummary(),
    ]);

  return (
    <SalesPage
      customers={customers}
      salesSlips={salesSlips}
      auctionLots={auctionLots}
      auctionSummary={auctionSummary}
    />
  );
}
