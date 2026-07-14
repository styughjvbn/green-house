"use client";

import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Plus } from "lucide-react";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import { StatusBadge } from "@/shared/ui/StatusBadge";
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
  const columns = useMemo<ColumnDef<InboundRecord, unknown>[]>(
    () => [
      {
        accessorKey: "inboundDate",
        header: "입고일",
        cell: ({ row }) => formatShortDate(row.original.inboundDate),
        size: 100,
      },
      {
        accessorKey: "inboundType",
        header: "유형",
        cell: ({ row }) => INBOUND_TYPE_LABELS[row.original.inboundType],
        size: 130,
      },
      {
        accessorKey: "varietyName",
        header: "품종명",
        size: 180,
        meta: { cellClassName: "font-semibold" },
      },
      {
        accessorKey: "estimatedQuantity",
        header: "예상",
        cell: ({ row }) => row.original.estimatedQuantity ?? "-",
        size: 80,
        meta: { align: "right" },
      },
      {
        accessorKey: "actualQuantity",
        header: "실제",
        cell: ({ row }) => row.original.actualQuantity ?? "-",
        size: 80,
        meta: { align: "right" },
      },
      {
        accessorKey: "currentLocation",
        header: "현재 위치",
        cell: ({ row }) => row.original.currentLocation ?? "-",
        size: 150,
      },
      {
        accessorKey: "status",
        header: "상태",
        cell: ({ row }) => (
          <StatusBadge tone={inboundStatusTone(row.original.status)}>
            {INBOUND_STATUS_LABELS[row.original.status]}
          </StatusBadge>
        ),
        size: 100,
      },
      {
        accessorKey: "pottingDueDate",
        header: "예정일",
        cell: ({ row }) => formatShortDate(row.original.pottingDueDate),
        size: 100,
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
          onClick={onOpenCreate}
        >
          <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />새
          입고 등록
        </button>
      }
      columns={columns}
      data={pageData.content}
      emptyMessage="조건에 맞는 입고 기록이 없습니다."
      getRowId={(row) => String(row.id)}
      pageIndex={pageData.page}
      pageSize={pageData.size}
      pageSizeOptions={[10, 20, 50]}
      selectedRowId={selectedId == null ? null : String(selectedId)}
      settingsKey="inventory.inboundRecords"
      title="입고 목록"
      totalLabel={`총 ${pageData.totalElements.toLocaleString()}건`}
      totalPages={pageData.totalPages}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      onRowClick={(row) => onSelect(row.id)}
    />
  );
}

function inboundStatusTone(status: InboundRecord["status"]) {
  if (status === "POTTED") return "green";
  if (status === "CANCELED") return "gray";
  return "blue";
}
