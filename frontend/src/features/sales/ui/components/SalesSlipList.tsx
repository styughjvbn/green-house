"use client";

import { Plus } from "lucide-react";
import { PaginationControls } from "@/shared/ui/PaginationControls";
import type { SalesSlipListItem } from "../../model/types";

export function SalesSlipList({
  currentPage,
  pageSize,
  salesSlips,
  selectedSalesSlipId,
  totalPages,
  totalSalesSlips,
  onSelect,
  onCreateSalesSlip,
  onPageChange,
  onPageSizeChange,
}: {
  currentPage: number;
  pageSize: number;
  salesSlips: SalesSlipListItem[];
  selectedSalesSlipId: number | null;
  totalPages: number;
  totalSalesSlips: number;
  onSelect: (salesSlipId: number) => void;
  onCreateSalesSlip: () => void;
  onPageChange: (pageIndex: number) => void;
  onPageSizeChange: (pageSize: number) => void;
}) {
  return (
    <section className="flex min-h-0 min-w-0 flex-col rounded-md border border-[#dfe5dc] bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <h2 className="text-xl font-bold text-[#17251b]">판매 전표 목록</h2>
          <span className="text-sm font-semibold text-[#159447]">
            총 {totalSalesSlips}건
          </span>
        </div>
        <button
          className="inline-flex h-10 items-center gap-2 rounded-md bg-[#159447] px-4 text-sm font-semibold text-white shadow-sm"
          type="button"
          onClick={onCreateSalesSlip}
        >
          <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          판매 전표 등록
        </button>
      </div>

      <div className="mt-4 min-h-0 flex-1 overflow-auto">
        <table className="w-full min-w-[720px] border-collapse text-left text-sm">
          <thead className="border-y border-[#dfe5dc] text-[#435047]">
            <tr>
              <th className="px-3 py-3 font-semibold whitespace-nowrap">
                전표번호
              </th>
              <th className="px-3 py-3 font-semibold whitespace-nowrap">
                판매일자
              </th>
              <th className="px-3 py-3 font-semibold whitespace-nowrap">
                판매 유형
              </th>
              <th className="px-3 py-3 font-semibold whitespace-nowrap">
                거래처
              </th>
              <th className="px-3 py-3 text-right font-semibold whitespace-nowrap">
                총 금액
              </th>
              <th className="px-3 py-3 font-semibold whitespace-nowrap">
                입금 상태
              </th>
              <th className="px-3 py-3 font-semibold whitespace-nowrap">
                판매 상태
              </th>
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
                  <td className="px-3 py-3 font-bold whitespace-nowrap text-[#159447]">
                    {slip.slipNumber}
                  </td>
                  <td className="px-3 py-3 whitespace-nowrap">
                    {slip.saleDate}
                  </td>
                  <td className="px-3 py-3 whitespace-nowrap">
                    {slip.salesType === "AUCTION" ? "경매" : "일반"}
                  </td>
                  <td className="max-w-[180px] px-3 py-3 font-semibold">
                    <span className="block truncate" title={slip.partner.name}>
                      {slip.partner.name}
                    </span>
                  </td>
                  <td className="px-3 py-3 text-right whitespace-nowrap">
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
      <div className="mt-4">
        <PaginationControls
          nextLabel="다음"
          pageCount={totalPages}
          pageIndex={currentPage}
          pageSize={pageSize}
          pageSizeOptions={[10, 20, 50]}
          previousLabel="이전"
          onPageChange={onPageChange}
          onPageSizeChange={onPageSizeChange}
        />
      </div>
    </section>
  );
}

function StatusBadge({ value }: { value: string }) {
  const tone =
    value === "미입금"
      ? "orange"
      : value === "작성중"
        ? "blue"
        : value === "취소"
          ? "gray"
          : "green";
  const classes = {
    blue: "bg-[#e6f0ff] text-[#246df2]",
    green: "bg-[#e7f7e8] text-[#16853b]",
    orange: "bg-[#fff1d6] text-[#d88400]",
    gray: "bg-[#eef1ee] text-[#657169]",
  }[tone];

  return (
    <span
      className={`inline-flex rounded-md px-2.5 py-1 text-xs font-bold whitespace-nowrap ${classes}`}
    >
      {value}
    </span>
  );
}
