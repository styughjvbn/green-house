"use client";

import { useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { workOperationScopeLabel } from "../../lib/workOperationDisplay";
import type { WorkOperationRelationSelection } from "../../lib/workOperationRelations";
import { workOperationRelationsQueryOptions } from "../../model/workRecordQueryOptions";
import { operationStatusLabel } from "../common/workOperationLabels";

export function WorkOperationRelationsCard({
  selection,
  selectedId,
  onClose,
  onSelect,
}: {
  selection: WorkOperationRelationSelection;
  selectedId: number | null;
  onClose: () => void;
  onSelect: (operationId: number) => void;
}) {
  const query = useQuery(
    workOperationRelationsQueryOptions(selection.operationId, selection.kind),
  );
  const title =
    selection.kind === "CREATION_BATCH" ? "함께 등록된 작업" : "연관 작업";

  return (
    <section
      className="max-h-64 shrink-0 overflow-hidden rounded-md border border-[#dfe5dc] bg-white shadow-sm"
      id="work-operation-relations"
    >
      <header className="flex items-center justify-between border-b border-[#e7ebe5] bg-[#f7f9f6] px-3 py-0.5">
        <div className="flex min-w-0 items-center gap-2">
          <h3 className="shrink-0 text-sm font-bold text-[#285b37]">{title}</h3>
          <span className="min-w-0 truncate text-xs text-[#738077]">
            {selection.operationTitle}
          </span>
        </div>
        <button
          aria-label={`${title} 닫기`}
          className="rounded p-1 text-[#5f6c63] hover:bg-[#e8eee6]"
          type="button"
          onClick={onClose}
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </header>

      <div className="max-h-48 overflow-auto">
        {query.isPending ? (
          <Message>관련 작업을 불러오는 중입니다.</Message>
        ) : query.isError ? (
          <Message>관련 작업을 불러오지 못했습니다.</Message>
        ) : !query.data.length ? (
          <Message>표시할 관련 작업이 없습니다.</Message>
        ) : (
          <table className="w-full min-w-[44rem] table-fixed text-left text-xs">
            <thead className="sticky top-0 bg-[#f7f9f6] text-[#4b584f]">
              <tr>
                <HeaderCell className="w-20">계획일</HeaderCell>
                <HeaderCell>작업명</HeaderCell>
                <HeaderCell className="w-24">작업 유형</HeaderCell>
                <HeaderCell className="w-20">범위</HeaderCell>
                <HeaderCell className="w-20">작업자</HeaderCell>
                <HeaderCell className="w-16 text-right">진행률</HeaderCell>
                <HeaderCell className="w-20">상태</HeaderCell>
              </tr>
            </thead>
            <tbody>
              {query.data.map((operation) => {
                const isReference = operation.id === selection.operationId;
                const isSelected = operation.id === selectedId;
                return (
                  <tr
                    aria-selected={isSelected}
                    className={
                      isSelected ? "bg-[#eaf7eb]" : "hover:bg-[#f5f9f4]"
                    }
                    key={operation.id}
                  >
                    <Cell>{formatShortDate(operation.plannedStartDate)}</Cell>
                    <Cell>
                      <div className="flex min-w-0 items-center gap-1">
                        {isReference ? (
                          <span className="shrink-0 rounded bg-[#dcebdc] px-1 py-px text-[9px] font-bold text-[#397046]">
                            기준
                          </span>
                        ) : null}
                        <button
                          className="min-w-0 flex-1 truncate text-left font-semibold text-[#1f3928] hover:underline"
                          title={operation.title}
                          type="button"
                          onClick={() => onSelect(operation.id)}
                        >
                          {operation.title}
                        </button>
                      </div>
                    </Cell>
                    <Cell>{operation.workType}</Cell>
                    <Cell>{workOperationScopeLabel(operation)}</Cell>
                    <Cell>{operation.worker || "-"}</Cell>
                    <Cell className="text-right">
                      {operation.progress.progressPercent}%
                    </Cell>
                    <Cell>{operationStatusLabel(operation.status)}</Cell>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}

function HeaderCell({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <th
      className={`border-r border-b border-[#e6ebe3] px-2.5 py-2 font-semibold last:border-r-0 ${className}`}
    >
      {children}
    </th>
  );
}

function Cell({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <td
      className={`truncate border-r border-b border-[#eef2ec] px-2.5 py-2 last:border-r-0 ${className}`}
    >
      {children}
    </td>
  );
}

function Message({ children }: { children: React.ReactNode }) {
  return (
    <p className="px-4 py-8 text-center text-xs text-[#6c786f]">{children}</p>
  );
}
