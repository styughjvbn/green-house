import {
  getAuctionLots,
  getAuctionTrackingSummary,
  getBusinessPartners,
  getSalesSlips,
  SalesPage,
} from "@/features/sales";

export const dynamic = "force-dynamic";

export default async function Page() {
  const [partners, salesSlips, auctionPage, auctionSummary] = await Promise.all(
    [
      getBusinessPartners(),
      getSalesSlips(),
      getAuctionLots(),
      getAuctionTrackingSummary(),
    ],
  );

  return (
    <SalesPage
      partners={partners}
      salesSlips={salesSlips}
      auctionPage={auctionPage}
      auctionSummary={auctionSummary}
    />
  );
}
