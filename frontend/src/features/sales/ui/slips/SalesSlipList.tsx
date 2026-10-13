"use client";

import { useMemo, type ReactNode } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import type { SalesSlipListItem } from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable, type DataTableToolbar } from "@/shared/ui/DataTable";
import { DataTableCreateButton } from "@/shared/ui/DataTableCreateButton";
import { SalesSlipStatusBadge } from "@/features/sales/ui/common/SalesStatusBadge";
export function SalesSlipList({
  toolbar,
  emptyAction,
  currentPage,
  pageSize,
  salesSlips,
  loading,
  selectedSalesSlipId,
  totalPages,
  totalSalesSlips,
  onSelect,
  onCreateSalesSlip,
  onPageChange,
  onPageSizeChange,
}: {
  toolbar?: DataTableToolbar;
  emptyAction?: ReactNode;
  currentPage: number;
  pageSize: number;
  salesSlips: SalesSlipListItem[];
  loading: boolean;
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
      // TODO: 수납 비활성화 — 전표 등록 마무리 후 연결 예정.
      {
        accessorKey: "salesStatus",
        header: "출고·출하",
        cell: ({ row }) => (
          <SalesSlipStatusBadge
            value={row.original.salesStatus}
            salesType={row.original.salesType}
          />
        ),
        size: 110,
      },
    ],
    [],
  );
  return (
    <DataTable
      toolbar={toolbar}
      emptyAction={emptyAction}
      actions={
        <DataTableCreateButton onClick={onCreateSalesSlip}>
          전표 작성
        </DataTableCreateButton>
      }
      columns={columns}
      data={salesSlips}
      emptyMessage="조건에 맞는 판매 전표가 없습니다."
      getRowId={(row) => String(row.id)}
      pageIndex={currentPage}
      isLoading={loading}
      pageSize={pageSize}
      pageSizeOptions={[10, 20, 50]}
      selectedRowId={
        selectedSalesSlipId == null ? null : String(selectedSalesSlipId)
      }
      settingsKey={"sales.slips"}
      title="전표 목록"
      totalLabel={`총 ${totalSalesSlips.toLocaleString()}건`}
      totalPages={totalPages}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      onRowClick={(row) => onSelect(row.id)}
    />
  );
}
