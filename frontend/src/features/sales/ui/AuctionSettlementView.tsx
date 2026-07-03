import type { AuctionLot } from "@/entities/farm/types";

export function AuctionSettlementView({ lots }: { lots: AuctionLot[] }) {
  const settlements = Object.values(
    lots.reduce<
      Record<
        string,
        {
          market: string;
          sold: number;
          amount: number;
          attempts: number;
          failed: number;
        }
      >
    >((result, lot) => {
      const current = result[lot.auctionMarket] ?? {
        market: lot.auctionMarket,
        sold: 0,
        amount: 0,
        attempts: 0,
        failed: 0,
      };
      current.sold += lot.soldQuantity;
      current.amount += lot.totalAmount;
      current.attempts += lot.attempts.length;
      current.failed += lot.failedCount;
      result[lot.auctionMarket] = current;
      return result;
    }, {}),
  );

  return (
    <section className="rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div>
        <h2 className="text-lg font-bold">경매장 정산 요약</h2>
        <p className="mt-1 text-sm text-[#68756c]">
          판매 완료 결과 기준 조회용 집계. 정산 확정과 전표 생성은 후속 범위.
        </p>
      </div>
      <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[720px] text-sm">
          <thead className="border-y border-[#dfe5dc] bg-[#f7f9f6]">
            <tr>
              {[
                "경매장",
                "판매 수량",
                "판매 금액",
                "평균 단가",
                "경매 횟수",
                "유찰 횟수",
                "유찰률",
              ].map((label) => (
                <th
                  key={label}
                  className="px-3 py-3 text-right first:text-left"
                >
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {settlements.map((item) => (
              <tr key={item.market} className="border-b border-[#edf0ec]">
                <td className="px-3 py-3 font-bold">{item.market}</td>
                <td className="px-3 py-3 text-right">
                  {item.sold.toLocaleString()}
                </td>
                <td className="px-3 py-3 text-right">
                  {item.amount.toLocaleString()}원
                </td>
                <td className="px-3 py-3 text-right">
                  {item.sold > 0
                    ? Math.round(item.amount / item.sold).toLocaleString()
                    : 0}
                  원
                </td>
                <td className="px-3 py-3 text-right">{item.attempts}</td>
                <td className="px-3 py-3 text-right">{item.failed}</td>
                <td className="px-3 py-3 text-right">
                  {item.attempts > 0
                    ? `${Math.round((item.failed / item.attempts) * 100)}%`
                    : "-"}
                </td>
              </tr>
            ))}
            {settlements.length === 0 ? (
              <tr>
                <td colSpan={7} className="py-12 text-center text-[#68756c]">
                  집계할 경매 판매 결과가 없습니다.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  );
}
