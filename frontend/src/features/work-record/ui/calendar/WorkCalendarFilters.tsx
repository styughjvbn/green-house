import type { ReactNode } from "react";
import type { WorkOperationStatus } from "@/entities/farm/types";
import {
  FilterGrid,
  FilterPanel,
  FilterResetButton,
  FilterSelect,
} from "@/shared/ui/FilterControls";

export function WorkCalendarFilters({
  scopeControl,
  status,
  onStatusChange,
}: {
  scopeControl?: ReactNode;
  status: WorkOperationStatus | "";
  onStatusChange: (status: WorkOperationStatus | "") => void;
}) {
  return (
    <FilterPanel>
      <FilterGrid className="sm:grid-cols-[auto_minmax(12rem,18rem)_max-content]">
        {scopeControl ? (
          <div>
            <span className="mb-1 block text-sm font-semibold text-[#344138]">
              조회 범위
            </span>
            {scopeControl}
          </div>
        ) : null}
        <FilterSelect
          label="상태"
          value={status}
          onChange={(value) =>
            onStatusChange(value as WorkOperationStatus | "")
          }
        >
          <option value="">모든 상태</option>
          <option value="PLANNED">계획</option>
          <option value="IN_PROGRESS">진행 중</option>
          <option value="PAUSED">일시중지</option>
          <option value="COMPLETED">완료</option>
          <option value="STOPPED">종료</option>
          <option value="CANCELED">취소</option>
        </FilterSelect>
        <FilterResetButton onClick={() => onStatusChange("")} />
      </FilterGrid>
    </FilterPanel>
  );
}
