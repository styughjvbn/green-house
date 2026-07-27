"use client";

import type { ReactNode } from "react";
import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import type { WorkOperation } from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import { workOperationScopeLabel } from "../../lib/workOperationDisplay";
import { operationStatusLabel } from "../components/workOperationPanelUtils";

export function WorkOperationDataTable({
  actions,
  emptyMessage,
  loading,
  operations,
  page,
  pageSize,
  selectedId,
  settingsKey,
  showActualEndDate = false,
  title,
  totalElements,
  totalPages,
  onPageChange,
  onPageSizeChange,
  onSelect,
}: {
  actions?: ReactNode;
  emptyMessage: string;
  loading: boolean;
  operations: WorkOperation[];
  page: number;
  pageSize: number;
  selectedId: number | null;
  settingsKey: string;
  showActualEndDate?: boolean;
  title: string;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onSelect: (id: number) => void;
}) {
  const columns = useMemo<ColumnDef<WorkOperation, unknown>[]>(() => {
    const dateColumns: ColumnDef<WorkOperation, unknown>[] = [
      {
        accessorKey: "plannedStartDate",
        header: "계획일",
        cell: ({ row }) => formatDateRange(row.original),
        size: 150,
        meta: { cellClassName: "whitespace-nowrap" },
      },
    ];

    if (showActualEndDate) {
      dateColumns.push({
        accessorKey: "actualEndAt",
        header: "종료일",
        cell: ({ row }) => formatShortDate(row.original.actualEndAt),
        size: 90,
      });
    }

    return [
      ...dateColumns,
      {
        id: "work",
        header: "작업",
        cell: ({ row }) => (
          <>
            <strong className="block truncate" title={row.original.title}>
              {row.original.title}
            </strong>
            <span className="text-[#738077]">{row.original.workType}</span>
          </>
        ),
        size: 220,
      },
      {
        id: "scope",
        header: "범위",
        cell: ({ row }) => workOperationScopeLabel(row.original),
        size: 120,
      },
      {
        accessorKey: "worker",
        header: "작업자",
        cell: ({ row }) => row.original.worker || "-",
        size: 90,
      },
      {
        id: "progress",
        header: "진행률",
        cell: ({ row }) => `${row.original.progress.progressPercent}%`,
        size: 75,
        meta: { align: "right" },
      },
      {
        accessorKey: "status",
        header: "상태",
        cell: ({ row }) => (
          <span className="inline-flex rounded-full bg-[#eef2ed] px-2 py-1 text-xs font-semibold text-[#435047]">
            {operationStatusLabel(row.original.status)}
          </span>
        ),
        size: 85,
      },
    ];
  }, [showActualEndDate]);

  return (
    <DataTable
      actions={actions}
      columns={columns}
      data={operations}
      emptyMessage={emptyMessage}
      getRowId={(row) => String(row.id)}
      isLoading={loading}
      pageIndex={page}
      pageSize={pageSize}
      pageSizeOptions={[10, 20, 50]}
      selectedRowId={selectedId == null ? null : String(selectedId)}
      settingsKey={settingsKey}
      title={title}
      totalLabel={`총 ${totalElements.toLocaleString()}건`}
      totalPages={totalPages}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      onRowClick={(row) => onSelect(row.id)}
    />
  );
}

function formatDateRange(operation: WorkOperation) {
  const from = formatShortDate(operation.plannedStartDate);
  const to = operation.plannedEndDate
    ? formatShortDate(operation.plannedEndDate)
    : null;
  return to && to !== from ? `${from} ~ ${to}` : from;
}
