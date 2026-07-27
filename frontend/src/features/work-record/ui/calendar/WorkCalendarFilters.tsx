import type { WorkOperationStatus } from "@/entities/farm/types";
import {
  FilterGrid,
  FilterPanel,
  FilterResetButton,
  FilterSelect,
} from "@/shared/ui/FilterControls";

export function WorkCalendarFilters({
  status,
  onStatusChange,
}: {
  status: WorkOperationStatus | "";
  onStatusChange: (status: WorkOperationStatus | "") => void;
}) {
  return (
    <FilterPanel>
      <FilterGrid className="grid-cols-[minmax(12rem,18rem)_max-content]">
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
          <option value="CORRECTED">보정됨</option>
          <option value="CANCELED">취소</option>
        </FilterSelect>
        <FilterResetButton onClick={() => onStatusChange("")} />
      </FilterGrid>
    </FilterPanel>
  );
}
