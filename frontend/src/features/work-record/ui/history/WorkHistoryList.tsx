import type { WorkOperation } from "@/entities/farm/types";
import { WorkOperationDataTable } from "../common/WorkOperationDataTable";

export function WorkHistoryList({
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
      emptyMessage="조건에 맞는 작업 이력이 없습니다."
      loading={loading}
      operations={operations}
      page={page}
      pageSize={pageSize}
      selectedId={selectedId}
      settingsKey="workRecord.history"
      showActualEndDate
      title="작업 이력"
      totalElements={totalElements}
      totalPages={totalPages}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      onSelect={onSelect}
    />
  );
}
