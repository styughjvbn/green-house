import type { House } from "@/entities/farm/types";
import type { FarmPlacementSelection } from "@/entities/farm/model/placement";
import { FarmPlacementField } from "@/entities/farm/ui/FarmPlacementPicker";
import { TextField } from "../../common/FormFields";
import {
  sourceLocationLabel,
  sourcePositionLabel,
  type AvailableSource,
} from "../../../model/work-types/structure-change/structureChangeExecutionModel";

export function StructureChangeSourceFields({
  availableSources,
  houses,
  inputQuantities,
  recordMode,
  releasedPlacements,
  selectedSourceIds,
  totalInput,
  onChangeInputQuantity,
  onChangeReleasedPlacement,
  onToggleSource,
}: {
  availableSources: AvailableSource[];
  houses: House[];
  inputQuantities: Record<number, string>;
  recordMode: boolean;
  releasedPlacements: Record<number, FarmPlacementSelection | null | undefined>;
  selectedSourceIds: Set<number>;
  totalInput: number;
  onChangeInputQuantity: (groupId: number, value: string) => void;
  onChangeReleasedPlacement: (
    groupId: number,
    placement: FarmPlacementSelection,
  ) => void;
  onToggleSource: (source: AvailableSource["group"]) => void;
}) {
  return (
    <section className="rounded-md border border-[#cfe0d2] bg-[#f7faf6] p-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-[#26352b]">이번 실행 원본</p>
          <p className="mt-0.5 text-xs text-[#6a766e]">
            계획 잔여 수량을 기본 작업 수량으로 선택했습니다.
          </p>
        </div>
        <span className="text-xs font-semibold text-[#10783a]">
          {selectedSourceIds.size}묶음 · {totalInput}분
        </span>
      </div>
      <div className="mt-3 grid gap-2 md:grid-cols-2">
        {availableSources.map(({ group, target }) => (
          <div
            className="grid items-center gap-2 rounded-md bg-white p-2 sm:grid-cols-[auto_minmax(0,1fr)_120px]"
            key={group.id}
          >
            <input
              className="h-4 w-4 accent-[#159447]"
              checked={selectedSourceIds.has(group.id)}
              disabled={recordMode}
              type="checkbox"
              onChange={() => onToggleSource(group)}
            />
            <span className="min-w-0 text-xs">
              <span className="block truncate font-semibold text-[#26352b]">
                {group.varietyName} · {sourceLocationLabel(group)}
              </span>
              <span className="text-[#6a766e]">
                현재 {group.quantity}분 ·{" "}
                {recordMode ? "기록 수량" : "계획 잔여"}{" "}
                {target.remainingQuantity}분{sourcePositionLabel(group)}
              </span>
            </span>
            <TextField
              disabled
              label="작업 수량"
              type="number"
              value={inputQuantities[group.id] ?? ""}
              onChange={(value) => onChangeInputQuantity(group.id, value)}
            />
            {selectedSourceIds.has(group.id) &&
            Number(inputQuantities[group.id]) < group.quantity &&
            group.startPosition != null &&
            group.endPosition != null ? (
              <div className="sm:col-span-2 sm:col-start-2">
                <FarmPlacementField
                  buttonPlaceholder="원본 뒤쪽에서 비울 칸 선택"
                  dialogDescription="이번에 꺼내 작업할 수량만큼 원본 배치의 맨 뒤쪽 연속 구간을 선택하세요."
                  dialogTitle="원본에서 비울 자리"
                  excludeOrchidGroupIds={[group.id]}
                  fieldLabel="원본에서 비울 자리"
                  houses={houses}
                  value={releasedPlacements[group.id] ?? null}
                  onChange={(placement) =>
                    onChangeReleasedPlacement(group.id, placement)
                  }
                />
                <p className="mt-1 text-[11px] text-[#6a766e]">
                  작업 수량 비율에 맞춰 뒤쪽 칸을 자동 선택했습니다. 실제로 비운
                  범위가 다르면 수정하세요.
                </p>
              </div>
            ) : null}
          </div>
        ))}
      </div>
    </section>
  );
}
