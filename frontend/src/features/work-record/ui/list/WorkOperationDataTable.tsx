"use client";

import type { ReactNode } from "react";
import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import type { WorkOperationSummary } from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { DataTable } from "@/shared/ui/DataTable";
import { workOperationScopeLabel } from "../../lib/workOperationDisplay";
import type { WorkOperationRelationSelection } from "../../lib/workOperationRelations";
import { operationStatusLabel } from "../common/workOperationLabels";

export function WorkOperationDataTable({
  actions,
  activeRelation,
  emptyMessage,
  loading,
  operations,
  page,
  pageSize,
  relatedOperationIds,
  selectedId,
  settingsKey,
  title,
  totalElements,
  totalPages,
  onPageChange,
  onPageSizeChange,
  onSelect,
  onShowRelations,
}: {
  actions?: ReactNode;
  activeRelation: WorkOperationRelationSelection | null;
  emptyMessage: string;
  loading: boolean;
  operations: WorkOperationSummary[];
  page: number;
  pageSize: number;
  relatedOperationIds: ReadonlySet<number>;
  selectedId: number | null;
  settingsKey: string;
  title: string;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onSelect: (id: number) => void;
  onShowRelations: (selection: WorkOperationRelationSelection) => void;
}) {
  const columns = useMemo<ColumnDef<WorkOperationSummary, unknown>[]>(
    () => [
      {
        accessorKey: "plannedStartDate",
        header: "계획일",
        cell: ({ row }) => formatDateRange(row.original),
        size: 100,
        meta: { cellClassName: "whitespace-nowrap" },
      },
      {
        id: "work",
        header: "작업명",
        cell: ({ row }) => (
          <>
            <strong className="block truncate" title={row.original.title}>
              {row.original.title}
            </strong>
            <RelationBadges
              activeRelation={activeRelation}
              operation={row.original}
              relatedOperationIds={relatedOperationIds}
              onShowRelations={onShowRelations}
            />
          </>
        ),
        size: 150,
      },
      {
        accessorKey: "workType",
        header: "작업 유형",
        size: 90,
      },
      {
        id: "scope",
        header: "범위",
        cell: ({ row }) => workOperationScopeLabel(row.original),
        size: 70,
      },
      {
        accessorKey: "worker",
        header: "작업자",
        cell: ({ row }) => row.original.worker || "-",
        size: 60,
      },
      {
        id: "progress",
        header: "진행률",
        cell: ({ row }) => `${row.original.progress.progressPercent}%`,
        size: 60,
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
    ],
    [activeRelation, onShowRelations, relatedOperationIds],
  );

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

function RelationBadges({
  activeRelation,
  operation,
  relatedOperationIds,
  onShowRelations,
}: {
  activeRelation: WorkOperationRelationSelection | null;
  operation: WorkOperationSummary;
  relatedOperationIds: ReadonlySet<number>;
  onShowRelations: (selection: WorkOperationRelationSelection) => void;
}) {
  const summary = operation.relationSummary;
  if (!summary) return null;
  return (
    <div className="mt-1 flex flex-wrap gap-1">
      {summary.creationBatchSize > 1 ? (
        <RelationBadge
          active={isActive(
            activeRelation,
            relatedOperationIds,
            operation.id,
            "CREATION_BATCH",
          )}
          label={`함께 등록 ${summary.creationBatchSize - 1}건`}
          onClick={() =>
            onShowRelations({
              operationId: operation.id,
              operationTitle: operation.title,
              kind: "CREATION_BATCH",
            })
          }
        />
      ) : null}
      {summary.linkedOperationCount > 0 ? (
        <RelationBadge
          active={isActive(
            activeRelation,
            relatedOperationIds,
            operation.id,
            "LINKED",
          )}
          label={`연관 작업 ${summary.linkedOperationCount}건`}
          onClick={() =>
            onShowRelations({
              operationId: operation.id,
              operationTitle: operation.title,
              kind: "LINKED",
            })
          }
        />
      ) : null}
    </div>
  );
}

function RelationBadge({
  active,
  label,
  onClick,
}: {
  active: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <div
      aria-controls="work-operation-relations"
      aria-expanded={active}
      className={`cursor-pointer rounded px-1.5 py-px text-[10px] font-semibold transition-colors ${
        active
          ? "bg-[#397046] text-white"
          : "bg-[#edf5ed] text-[#397046] hover:bg-[#dcebdc]"
      }`}
      role="button"
      tabIndex={0}
      onClick={(event) => {
        event.stopPropagation();
        onClick();
      }}
      onKeyDown={(event) => {
        if (event.key !== "Enter" && event.key !== " ") return;
        event.preventDefault();
        event.stopPropagation();
        onClick();
      }}
    >
      {label}
    </div>
  );
}

function isActive(
  selection: WorkOperationRelationSelection | null,
  relatedOperationIds: ReadonlySet<number>,
  operationId: number,
  kind: WorkOperationRelationSelection["kind"],
) {
  return selection?.kind === kind && relatedOperationIds.has(operationId);
}

function formatDateRange(operation: WorkOperationSummary) {
  const from = formatShortDate(operation.plannedStartDate);
  const to = operation.plannedEndDate
    ? formatShortDate(operation.plannedEndDate)
    : null;
  return to && to !== from ? `${from} ~ ${to}` : from;
}
