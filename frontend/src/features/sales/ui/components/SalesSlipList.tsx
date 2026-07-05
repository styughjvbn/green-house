"use client";

import type { SalesSlip } from "@/entities/farm/types";

export function SalesSlipList({
  salesSlips,
  selectedSalesSlipId,
  onSelect,
}: {
  salesSlips: SalesSlip[];
  selectedSalesSlipId: number | null;
  onSelect: (salesSlipId: number) => void;
}) {
  return (
    <section className="min-w-0 rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="flex items-center gap-2">
        <h2 className="text-xl font-bold text-[#17251b]">판매 전표 목록</h2>
        <span className="text-sm font-semibold text-[#159447]">
          총 {salesSlips.length}건
        </span>
      </div>

      <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[650px] border-collapse text-left text-sm">
          <thead className="border-y border-[#dfe5dc] text-[#435047]">
            <tr>
              <th className="px-3 py-3 font-semibold">전표번호</th>
              <th className="px-3 py-3 font-semibold">판매일자</th>
              <th className="px-3 py-3 font-semibold">판매 유형</th>
              <th className="px-3 py-3 font-semibold">거래처</th>
              <th className="px-3 py-3 text-right font-semibold">총 금액</th>
              <th className="px-3 py-3 font-semibold">입금 상태</th>
              <th className="px-3 py-3 font-semibold">판매 상태</th>
            </tr>
          </thead>
          <tbody>
            {salesSlips.map((slip) => {
              const selected = selectedSalesSlipId === slip.id;

              return (
                <tr
                  key={slip.id}
                  className={`cursor-pointer border-b border-[#edf0ec] transition hover:bg-[#eef7ec] ${
                    selected ? "bg-[#eaf7eb]" : "bg-white"
                  }`}
                  onClick={() => onSelect(slip.id)}
                >
                  <td className="px-3 py-3 font-bold text-[#159447]">
                    {slip.slipNumber}
                  </td>
                  <td className="px-3 py-3">{slip.saleDate}</td>
                  <td className="px-3 py-3">
                    {slip.salesType === "AUCTION" ? "경매" : "일반"}
                  </td>
                  <td className="px-3 py-3 font-semibold">
                    {slip.partner.name}
                  </td>
                  <td className="px-3 py-3 text-right">
                    {slip.totalAmount.toLocaleString()}
                  </td>
                  <td className="px-3 py-3">
                    <StatusBadge value={slip.paymentStatus} />
                  </td>
                  <td className="px-3 py-3">
                    <StatusBadge value={slip.salesStatus} />
                  </td>
                </tr>
              );
            })}
            {salesSlips.length === 0 ? (
              <tr>
                <td
                  className="px-3 py-12 text-center text-[#5c6a60]"
                  colSpan={7}
                >
                  조건에 맞는 판매 전표가 없습니다.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function StatusBadge({ value }: { value: string }) {
  const tone =
    value === "미입금" ? "orange" : value === "작성중" ? "blue" : "green";
  const classes = {
    blue: "bg-[#e6f0ff] text-[#246df2]",
    green: "bg-[#e7f7e8] text-[#16853b]",
    orange: "bg-[#fff1d6] text-[#d88400]",
  }[tone];

  return (
    <span className={`rounded-md px-2.5 py-1 text-xs font-bold ${classes}`}>
      {value}
    </span>
  );
}
