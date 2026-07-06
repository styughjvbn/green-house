import {
  getAuctionLots,
  getAuctionTrackingSummary,
  getAuctionSettlements,
  getBusinessPartners,
  getSalesSlips,
  SalesPage,
} from "@/features/sales";

export const dynamic = "force-dynamic";

export default async function Page() {
  const [
    partners,
    salesSlips,
    auctionPage,
    auctionSummary,
    auctionSettlements,
  ] = await Promise.all([
    getBusinessPartners(),
    getSalesSlips(),
    getAuctionLots(),
    getAuctionTrackingSummary(),
    getAuctionSettlements(),
  ]);

  return (
    <SalesPage
      partners={partners}
      salesSlips={salesSlips}
      auctionPage={auctionPage}
      auctionSummary={auctionSummary}
      auctionSettlements={auctionSettlements}
    />
  );
}
