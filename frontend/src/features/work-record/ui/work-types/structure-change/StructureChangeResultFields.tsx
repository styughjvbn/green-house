import { Plus, Trash2 } from "lucide-react";
import type { House, OrchidGroup } from "@/entities/farm/types";
import type {
  FarmPlacementReference,
  FarmPlacementSelection,
} from "@/entities/farm/model/placement";
import { FarmPlacementField } from "@/entities/farm/ui/FarmPlacementPicker";
import { POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";
import { TextField } from "../../common/FormFields";
import {
  resultReferencePlacements,
  sourceReferencePlacements,
  type AvailableSource,
  type ResultPurpose,
  type ResultRow,
  type StructureChangeOperation,
} from "../../../model/work-types/structure-change/structureChangeExecutionModel";

export function StructureChangeResultFields({
  commonAgeYear,
  commonPotSize,
  houses,
  inputQuantities,
  operation,
  orchidGroups,
  releasedPlacements,
  rows,
  savedResultReferences,
  selectedSources,
  onAdd,
  onChange,
  onRemove,
  onSetAllAgeYears,
  onSetAllPotSizes,
}: {
  commonAgeYear: string;
  commonPotSize: string;
  houses: House[];
  inputQuantities: Record<number, string>;
  operation: StructureChangeOperation;
  orchidGroups: OrchidGroup[];
  releasedPlacements: Record<number, FarmPlacementSelection | null | undefined>;
  rows: ResultRow[];
  savedResultReferences: FarmPlacementReference[];
  selectedSources: AvailableSource[];
  onAdd: () => void;
  onChange: (key: string, patch: Partial<ResultRow>) => void;
  onRemove: (row: ResultRow) => void;
  onSetAllAgeYears: (ageYear: string) => void;
  onSetAllPotSizes: (potSize: string) => void;
}) {
  const excludedSourceIds = selectedSources
    .map(({ group }) => group.id)
    .filter((sourceId) => {
      const source = orchidGroups.find((group) => group.id === sourceId);
      return (
        source != null &&
        (Number(inputQuantities[sourceId]) === source.quantity ||
          releasedPlacements[sourceId] != null)
      );
    });
  const sourceReferences = sourceReferencePlacements(selectedSources);

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-bold">결과 난 묶음</p>
          <p className="mt-0.5 text-xs text-[#6a766e]">
            원본별 속성·수량·현재 배치를 기본값으로 추론했습니다.
          </p>
        </div>
        <button
          className="inline-flex items-center gap-1 rounded border px-2 py-1 text-xs"
          type="button"
          onClick={onAdd}
        >
          <Plus className="h-3 w-3" aria-hidden="true" /> 결과 분리
        </button>
      </div>
      <div className="grid gap-2 rounded-md border border-[#dfe7dd] bg-white p-3 sm:grid-cols-2">
        <label className="block text-sm font-semibold text-[#435047]">
          전체 결과 화분 크기
          <select
            className="mt-1 w-full rounded-md border border-[#cfd8cc] bg-white px-2 py-2 font-normal"
            value={commonPotSize}
            onChange={(event) => onSetAllPotSizes(event.target.value)}
          >
            <option value="">결과별 설정</option>
            {POT_SIZE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <TextField
          label="전체 결과 시작 년생"
          type="number"
          value={commonAgeYear}
          onChange={onSetAllAgeYears}
        />
      </div>
      {rows.map((row, index) => (
        <ResultRowFields
          excludeOrchidGroupIds={excludedSourceIds}
          houses={houses}
          index={index}
          key={row.key}
          operation={operation}
          referencePlacements={[
            ...sourceReferences,
            ...savedResultReferences,
            ...resultReferencePlacements(rows, row.key),
          ]}
          removable={rows.length > 1}
          row={row}
          onChange={(patch) => onChange(row.key, patch)}
          onRemove={() => onRemove(row)}
        />
      ))}
    </section>
  );
}

function ResultRowFields({
  excludeOrchidGroupIds,
  houses,
  index,
  operation,
  referencePlacements,
  removable,
  row,
  onChange,
  onRemove,
}: {
  excludeOrchidGroupIds: number[];
  houses: House[];
  index: number;
  operation: Pick<StructureChangeOperation, "workTypeCode" | "workType">;
  referencePlacements: FarmPlacementReference[];
  removable: boolean;
  row: ResultRow;
  onChange: (patch: Partial<ResultRow>) => void;
  onRemove: () => void;
}) {
  return (
    <section className="rounded-md border bg-[#f8faf7] p-3">
      <div className="mb-2 flex items-center justify-between">
        <p className="text-xs font-bold">
          결과 {index + 1} · 원본 {row.sourceOrchidGroupIds.length}묶음
        </p>
        {removable ? (
          <button
            className="inline-flex items-center gap-1 text-xs font-semibold text-[#7b4b3e]"
            type="button"
            onClick={onRemove}
          >
            {operation.workTypeCode === "MERGE"
              ? "다른 결과에 합치기"
              : "결과 합치기"}
            <Trash2 className="h-4 w-4 text-[#a33a24]" aria-hidden="true" />
          </button>
        ) : null}
      </div>
      <div className="grid gap-2 sm:grid-cols-4">
        <div className="sm:col-span-4">
          <FarmPlacementField
            dialogDescription="자동 선택된 위치를 확인하거나 결과 난 묶음의 새 위치를 지정하세요."
            dialogTitle={`${operation.workType} 결과 ${index + 1} 배치 위치`}
            fieldLabel="결과 배치"
            excludeOrchidGroupIds={excludeOrchidGroupIds}
            houses={houses}
            referencePlacements={referencePlacements}
            value={row.placement}
            onChange={(placement) => onChange({ placement })}
          />
        </div>
        <TextField
          label="결과 수량"
          type="number"
          value={row.quantity}
          onChange={(quantity) => onChange({ quantity, autoQuantity: false })}
        />
        <label className="block text-sm font-semibold text-[#435047]">
          화분 크기
          <select
            className="mt-1 w-full rounded-md border border-[#cfd8cc] bg-white px-2 py-2 font-normal"
            value={row.potSize}
            onChange={(event) => onChange({ potSize: event.target.value })}
          >
            {POT_SIZE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <TextField
          label="시작 년생"
          type="number"
          value={row.ageYear}
          onChange={(ageYear) => onChange({ ageYear })}
        />
        <label className="block text-sm font-semibold text-[#435047]">
          결과 구분
          <select
            className="mt-1 w-full rounded-md border border-[#cfd8cc] bg-white px-2 py-2 font-normal"
            value={row.purpose}
            onChange={(event) =>
              onChange({ purpose: event.target.value as ResultPurpose })
            }
          >
            <option value="NORMAL">일반 결과</option>
            <option value="DIVIDE_CANDIDATE">분주 후보</option>
            <option value="HELD">별도 보관</option>
          </select>
        </label>
      </div>
    </section>
  );
}
