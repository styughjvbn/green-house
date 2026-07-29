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
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onSelect: (id: number) => void;
}) {
  return (
    <WorkOperationDataTable
      actions={headerActions}
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
