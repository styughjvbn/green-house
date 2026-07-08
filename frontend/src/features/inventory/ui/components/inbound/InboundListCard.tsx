"use client";

import { Plus } from "lucide-react";
import { PaginationControls } from "@/shared/ui/PaginationControls";
import type { InboundRecord, InventoryPageResult } from "../../../model/types";
import {
  INBOUND_STATUS_LABELS,
  INBOUND_TYPE_LABELS,
} from "../../../lib/inboundUi";

export function InboundListCard({
  pageData,
  selectedId,
  onSelect,
  onOpenCreate,
  onPageChange,
  onPageSizeChange,
}: {
  pageData: InventoryPageResult<InboundRecord>;
  selectedId?: number;
  onSelect: (id: number) => void;
  onOpenCreate: () => void;
  onPageChange: (pageIndex: number) => void;
  onPageSizeChange: (pageSize: number) => void;
}) {
  return (
    <section className="min-w-0 rounded-md border border-[#dce2dc] bg-white p-3 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-bold">
          입고 목록
          <span className="ml-2 text-xs font-semibold text-[#159447]">
            총 {pageData.totalElements}건
          </span>
        </h2>
        <button
          className="flex items-center gap-2 rounded-md bg-[#159447] px-3 py-2 text-xs font-semibold text-white shadow-sm"
          type="button"
          onClick={onOpenCreate}
        >
          <Plus className="h-3.5 w-3.5" />새 입고 등록
        </button>
      </div>
      <div className="mt-3 overflow-x-auto">
        <table className="w-full min-w-[880px] text-xs">
          <thead className="border-y border-[#dce2dc] bg-[#f7f9f6] text-[#536057]">
            <tr>
              {[
                "입고일",
                "유형",
                "품종명",
                "예상",
                "실제",
                "현재 위치",
                "상태",
                "예정일",
              ].map((label) => (
                <th className="px-3 py-2 text-left font-semibold" key={label}>
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {pageData.content.map((item) => (
              <tr
                key={item.id}
                className={`cursor-pointer border-b border-[#e5e9e5] hover:bg-[#f3f9f3] ${item.id === selectedId ? "bg-[#eaf7eb]" : ""}`}
                onClick={() => onSelect(item.id)}
              >
                <td className="px-3 py-2">{item.inboundDate}</td>
                <td className="px-3 py-2">
                  {INBOUND_TYPE_LABELS[item.inboundType]}
                </td>
                <td className="px-3 py-2 font-semibold">{item.varietyName}</td>
                <td className="px-3 py-2">
                  {item.estimatedQuantity ?? item.bottleCount ?? "-"}
                </td>
                <td className="px-3 py-2">{item.actualQuantity ?? "-"}</td>
                <td className="px-3 py-2">{item.currentLocation ?? "-"}</td>
                <td className="px-3 py-2">
                  {INBOUND_STATUS_LABELS[item.status]}
                </td>
                <td className="px-3 py-2">{item.pottingDueDate ?? "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-3 flex items-center justify-between gap-3">
        <PaginationControls
          nextLabel="다음"
          pageCount={pageData.totalPages}
          pageIndex={pageData.page}
          pageSize={pageData.size}
          pageSizeOptions={[10, 20, 50]}
          previousLabel="이전"
          onPageChange={onPageChange}
          onPageSizeChange={onPageSizeChange}
        />
      </div>
    </section>
  );
}
