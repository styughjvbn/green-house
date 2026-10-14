"use client";

import { useMemo, type ReactNode } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import type { Page } from "@/shared/api/page";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable, type DataTableToolbar } from "@/shared/ui/DataTable";
import { DataTableCreateButton } from "@/shared/ui/DataTableCreateButton";
import { StatusBadge } from "@/shared/ui/StatusBadge";
import type { InboundRecord } from "../../model/types";
import {
  INBOUND_STATUS_LABELS,
  INBOUND_TYPE_LABELS,
} from "../../lib/inboundUi";

export function InboundList({
  toolbar,
  emptyAction,
  hasAppliedFilters = false,
  pageData,
  loading,
  selectedId,
  onSelect,
  onOpenCreate,
  onPageChange,
  onPageSizeChange,
}: {
  toolbar?: DataTableToolbar;
  emptyAction?: ReactNode;
  hasAppliedFilters?: boolean;
  pageData: Page<InboundRecord>;
  loading: boolean;
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
        id: "placedQuantity",
        header: "배치 수량",
        cell: ({ row }) =>
          row.original.createdOrchidGroups.length
            ? row.original.createdOrchidGroups.reduce(
                (sum, group) => sum + group.quantity,
                0,
              )
            : "-",
        size: 80,
        meta: { align: "right" },
      },
      {
        id: "locations",
        header: "배치 위치",
        cell: ({ row }) => {
          const locations = [
            ...new Set(
              row.original.createdOrchidGroups.map((group) => group.location),
            ),
          ];
          return locations.length
            ? locations.join(", ")
            : (row.original.tempLocation ?? "-");
        },
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
      {
        accessorKey: "pottingDate",
        header: "포트 작업일",
        cell: ({ row }) =>
          row.original.inboundType === "FLASK_SEEDLING"
            ? formatShortDate(row.original.pottingDate)
            : "-",
        size: 100,
      },
    ],
    [],
  );

  return (
    <DataTable
      toolbar={toolbar}
      emptyAction={emptyAction}
      hasAppliedFilters={hasAppliedFilters}
      actions={
        <DataTableCreateButton onClick={onOpenCreate}>
          새 입고 등록
        </DataTableCreateButton>
      }
      columns={columns}
      data={pageData.content}
      emptyMessage="등록된 입고 기록이 없습니다."
      filteredEmptyMessage="조건에 맞는 입고 기록이 없습니다."
      getRowId={(row) => String(row.id)}
      isLoading={loading}
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
  if (status === "CANCELED") return "gray";
  return "blue";
}
