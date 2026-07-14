"use client";

import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Plus } from "lucide-react";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import type { SalesSlipListItem } from "../../model/types";
import { SalesSlipStatusBadge } from "../common/SalesStatusBadge";

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
  const columns = useMemo<ColumnDef<SalesSlipListItem, unknown>[]>(
    () => [
      {
        accessorKey: "saleDate",
        header: "판매일자",
        cell: ({ row }) => formatShortDate(row.original.saleDate),
        size: 80,
        meta: { hideable: false },
      },
      {
        id: "salesType",
        header: "판매 유형",
        size: 70,
        cell: ({ row }) =>
          row.original.salesType === "AUCTION" ? "경매" : "일반",
      },
      {
        id: "partnerName",
        header: "거래처",
        cell: ({ row }) => (
          <span className="block truncate" title={row.original.partner.name}>
            {row.original.partner.name}
          </span>
        ),
        size: 190,
        meta: { cellClassName: "max-w-[180px] font-semibold" },
      },
      {
        accessorKey: "totalAmount",
        header: "총 금액",
        cell: ({ row }) => row.original.totalAmount.toLocaleString(),
        size: 120,
        meta: { align: "right", cellClassName: "whitespace-nowrap" },
      },
      {
        accessorKey: "paymentStatus",
        header: "입금 상태",
        cell: ({ row }) => (
          <SalesSlipStatusBadge value={row.original.paymentStatus} />
        ),
        size: 10,
      },
      {
        accessorKey: "salesStatus",
        header: "판매 상태",
        cell: ({ row }) => (
          <SalesSlipStatusBadge value={row.original.salesStatus} />
        ),
        size: 110,
      },
    ],
    [],
  );

  return (
    <DataTable
      actions={
        <button
          className="inline-flex h-9 items-center gap-2 rounded-md bg-[#159447] px-3 text-xs font-semibold whitespace-nowrap text-white shadow-sm"
          type="button"
          onClick={onCreateSalesSlip}
        >
          <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          판매 전표 등록
        </button>
      }
      columns={columns}
      data={salesSlips}
      emptyMessage="조건에 맞는 판매 전표가 없습니다."
      getRowId={(row) => String(row.id)}
      pageIndex={currentPage}
      pageSize={pageSize}
      pageSizeOptions={[10, 20, 50]}
      selectedRowId={
        selectedSalesSlipId == null ? null : String(selectedSalesSlipId)
      }
      settingsKey="sales.slips"
      title="판매 전표 목록"
      totalLabel={`총 ${totalSalesSlips.toLocaleString()}건`}
      totalPages={totalPages}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      onRowClick={(row) => onSelect(row.id)}
    />
  );
}
