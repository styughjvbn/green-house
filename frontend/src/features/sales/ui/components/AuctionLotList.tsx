import type { AuctionLot } from "@/entities/farm/types";
import { PaginationControls } from "@/shared/ui/PaginationControls";
import {
  auctionInspectionLabel,
  auctionStatusLabel,
  auctionStatusTone,
} from "../../lib/auctionDisplay";

export function AuctionLotList({
  lots,
  page,
  pageSize,
  totalElements,
  totalPages,
  selectedId,
  onSelect,
  onPageChange,
  onPageSizeChange,
}: {
  lots: AuctionLot[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}) {
  return (
    <section className="min-w-0 rounded-md border border-[#dfe5dc] bg-white shadow-sm">
      <div className="flex items-center justify-between border-b border-[#e7ebe5] px-4 py-3">
        <h2 className="text-base font-bold text-[#17251b]">출하 lot 목록</h2>
        <span className="text-xs font-semibold text-[#159447]">
          총 {totalElements.toLocaleString()}건
        </span>
      </div>
      <div className="max-h-[590px] overflow-auto">
        <table className="w-full min-w-[1160px] border-collapse text-left text-xs">
          <thead className="sticky top-0 z-10 bg-[#f7f9f6] text-[#4b584f]">
            <tr>
              {[
                "출하일",
                "경매장",
                "품목 / 품종",
                "등급",
                "출하",
                "판매",
                "대기",
                "반환",
                "최근 경매",
                "유찰",
                "상태",
                "금액",
                "검수",
              ].map((label) => (
                <th
                  key={label}
                  className="border-b border-[#dfe5dc] px-2.5 py-2.5 font-semibold whitespace-nowrap"
                >
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {lots.map((lot) => (
              <tr
                key={lot.id}
                className={`cursor-pointer border-b border-[#edf0ec] hover:bg-[#f0f8ef] ${selectedId === lot.id ? "bg-[#eaf7eb]" : ""}`}
                onClick={() => onSelect(lot.id)}
              >
                <td className="px-2.5 py-2.5 whitespace-nowrap">
                  {lot.shipmentDate}
                </td>
                <td className="px-2.5 py-2.5 font-semibold whitespace-nowrap">
                  {lot.auctionMarket}
                </td>
                <td className="px-2.5 py-2.5">
                  <strong className="block">{lot.varietyName}</strong>
                  <span className="text-[#738077]">{lot.itemName}</span>
                </td>
                <td className="px-2.5 py-2.5">{lot.shipmentGrade || "-"}</td>
                <NumberCell value={lot.shippedQuantity} />
                <NumberCell value={lot.soldQuantity} />
                <NumberCell
                  value={lot.waitingQuantity}
                  emphasize={lot.waitingQuantity > 0}
                />
                <NumberCell value={lot.returnedQuantity} />
                <td className="px-2.5 py-2.5 whitespace-nowrap">
                  {lot.latestAuctionDate || "-"}
                </td>
                <td className="px-2.5 py-2.5 text-center">{lot.failedCount}</td>
                <td className="px-2.5 py-2.5">
                  <StatusBadge status={lot.currentStatus} />
                </td>
                <td className="px-2.5 py-2.5 text-right whitespace-nowrap">
                  {lot.totalAmount.toLocaleString()}
                </td>
                <td className="px-2.5 py-2.5 whitespace-nowrap">
                  {auctionInspectionLabel(lot.inspectionStatus)}
                </td>
              </tr>
            ))}
            {lots.length === 0 ? (
              <tr>
                <td
                  className="py-14 text-center text-sm text-[#6c786f]"
                  colSpan={13}
                >
                  조건에 맞는 출하 lot이 없습니다.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
      <Pagination
        page={page}
        pageSize={pageSize}
        totalPages={totalPages}
        onPageChange={onPageChange}
        onPageSizeChange={onPageSizeChange}
      />
    </section>
  );
}

function Pagination({
  page,
  pageSize,
  totalPages,
  onPageChange,
  onPageSizeChange,
}: {
  page: number;
  pageSize: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[#e7ebe5] px-3 py-2.5">
      <PaginationControls
        nextLabel="다음"
        pageCount={totalPages}
        pageIndex={page - 1}
        pageSize={pageSize}
        pageSizeOptions={[20, 50, 100]}
        previousLabel="이전"
        onPageChange={(pageIndex) => onPageChange(pageIndex + 1)}
        onPageSizeChange={onPageSizeChange}
      />
    </div>
  );
}

function NumberCell({
  value,
  emphasize = false,
}: {
  value: number;
  emphasize?: boolean;
}) {
  return (
    <td
      className={`px-2.5 py-2.5 text-right font-semibold ${emphasize ? "text-[#d67a00]" : ""}`}
    >
      {value.toLocaleString()}
    </td>
  );
}

export function StatusBadge({
  status,
}: {
  status: AuctionLot["currentStatus"];
}) {
  const tone = auctionStatusTone(status);
  const classes = {
    green: "bg-[#e5f5e8] text-[#16853b]",
    orange: "bg-[#fff1d8] text-[#c66f00]",
    red: "bg-[#fee9e7] text-[#c43d35]",
    blue: "bg-[#e9f1fb] text-[#286aa6]",
  }[tone];
  return (
    <span
      className={`inline-flex rounded px-2 py-1 text-[11px] font-bold whitespace-nowrap ${classes}`}
    >
      {auctionStatusLabel(status)}
    </span>
  );
}
