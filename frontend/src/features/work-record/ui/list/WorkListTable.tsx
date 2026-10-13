import type { ReactNode } from "react";
import type { DataTableToolbar } from "@/shared/ui/DataTable";
import type { WorkOperationSummary } from "@/entities/farm/types";
import type { WorkOperationRelationSelection } from "../../lib/workOperationRelations";
import { WorkOperationDataTable } from "./WorkOperationDataTable";

export function WorkListTable({
  emptyAction,
  headerActions,
  toolbar,
  activeRelation,
  loading,
  operations,
  page,
  pageSize,
  relatedOperationIds,
  selectedId,
  totalElements,
  totalPages,
  onPageChange,
  onPageSizeChange,
  onSelect,
  onShowRelations,
}: {
  emptyAction?: ReactNode;
  headerActions?: ReactNode;
  toolbar?: DataTableToolbar;
  activeRelation: WorkOperationRelationSelection | null;
  loading: boolean;
  operations: WorkOperationSummary[];
  page: number;
  pageSize: number;
  relatedOperationIds: ReadonlySet<number>;
  selectedId: number | null;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onSelect: (id: number) => void;
  onShowRelations: (selection: WorkOperationRelationSelection) => void;
}) {
  return (
    <WorkOperationDataTable
      emptyAction={emptyAction}
      actions={headerActions}
      activeRelation={activeRelation}
      emptyMessage="조건에 맞는 작업이 없습니다."
      loading={loading}
      operations={operations}
      page={page}
      pageSize={pageSize}
      relatedOperationIds={relatedOperationIds}
      selectedId={selectedId}
      settingsKey="workRecord.management"
      title="작업 목록"
      toolbar={toolbar}
      totalElements={totalElements}
      totalPages={totalPages}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      onSelect={onSelect}
      onShowRelations={onShowRelations}
    />
  );
}
