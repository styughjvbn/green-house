import type { ReactNode } from "react";
import { Plus } from "lucide-react";
import type { WorkOperation } from "@/entities/farm/types";
import { WorkOperationDataTable } from "./WorkOperationDataTable";

export function WorkListTable({
  headerActions,
  loading,
  operations,
  page,
  pageSize,
  selectedId,
  totalElements,
  totalPages,
  onCreate,
  onPageChange,
  onPageSizeChange,
  onSelect,
}: {
  headerActions?: ReactNode;
  loading: boolean;
  operations: WorkOperation[];
  page: number;
  pageSize: number;
  selectedId: number | null;
  totalElements: number;
  totalPages: number;
  onCreate?: () => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onSelect: (id: number) => void;
}) {
  return (
    <WorkOperationDataTable
      actions={
        headerActions || onCreate ? (
          <div className="flex max-w-full flex-wrap items-center justify-end gap-2">
            {headerActions}
            {onCreate ? (
              <button
                className="inline-flex h-9 items-center gap-2 rounded-md bg-[#159447] px-3 text-xs font-semibold whitespace-nowrap text-white shadow-sm"
                type="button"
                onClick={onCreate}
              >
                <Plus
                  className="h-4 w-4"
                  strokeWidth={1.8}
                  aria-hidden="true"
                />
                작업 등록
              </button>
            ) : null}
          </div>
        ) : null
      }
      emptyMessage="조건에 맞는 작업이 없습니다."
      loading={loading}
      operations={operations}
      page={page}
      pageSize={pageSize}
      selectedId={selectedId}
      settingsKey="workRecord.management"
      title="작업 목록"
      totalElements={totalElements}
      totalPages={totalPages}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      onSelect={onSelect}
    />
  );
}
