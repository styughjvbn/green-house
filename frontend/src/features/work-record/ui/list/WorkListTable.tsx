import type { ReactNode } from "react";
import { Plus } from "lucide-react";
import type { WorkOperationSummary } from "@/entities/farm/types";
import type { WorkOperationRelationSelection } from "../../lib/workOperationRelations";
import { WorkOperationDataTable } from "./WorkOperationDataTable";

export function WorkListTable({
  headerActions,
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
  headerActions?: ReactNode;
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
      totalElements={totalElements}
      totalPages={totalPages}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      onSelect={onSelect}
      onShowRelations={onShowRelations}
    />
  );
}
