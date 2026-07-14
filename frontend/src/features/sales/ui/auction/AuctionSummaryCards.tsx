import type { AuctionTrackingSummary } from "@/entities/farm/types";

export function AuctionSummaryCards({
  summary,
}: {
  summary: AuctionTrackingSummary;
}) {
  const items = [
    ["출하 lot", `${summary.lotCount.toLocaleString()}건`],
    ["출하 수량", `${summary.shippedQuantity.toLocaleString()}분`],
    ["판매 수량", `${summary.soldQuantity.toLocaleString()}분`],
    ["경매장 대기", `${summary.waitingQuantity.toLocaleString()}분`],
    ["반환", `${summary.returnedQuantity.toLocaleString()}분`],
    ["확인 필요", `${summary.reviewRequiredCount.toLocaleString()}건`],
    ["경매 매출", `${summary.totalAmount.toLocaleString()}원`],
  ];

  return (
    <section className="grid grid-cols-2 gap-2 md:grid-cols-4 2xl:grid-cols-7">
      {items.map(([label, value]) => (
        <div
          key={label}
          className="rounded-md border border-[#dfe5dc] bg-white px-3 py-2.5 shadow-sm"
        >
          <p className="text-xs font-semibold text-[#68756c]">{label}</p>
          <p className="mt-1 text-base font-bold text-[#17251b]">{value}</p>
        </div>
      ))}
    </section>
  );
}
