"use client";

import { X } from "lucide-react";
import type { House, OrchidGroup, WorkOperation } from "@/entities/farm/types";
import type { FarmPlacementReference } from "@/entities/farm/model/placement";
import {
  WorkRecordVarietyNavigation,
  type WorkRecordNavigationItem,
} from "@/shared/ui/WorkRecordVarietyNavigation";
import type { StructureChangeExecutionPayload } from "../../../api/workRecordApi";
import { TextField } from "../../common/FormFields";
import { StructureChangeResultFields } from "./StructureChangeResultFields";
import { StructureChangeSourceFields } from "./StructureChangeSourceFields";
import {
  sourceLocationLabel,
  type ResultRow,
  type StructureChangeOperation,
} from "../../../model/work-types/structure-change/structureChangeExecutionModel";
import { allocateMovementQuantities } from "../../../model/work-types/structure-change/movementQuantityAllocation";
import { useStructureChangeExecution } from "../../../model/work-types/structure-change/useStructureChangeExecution";

export function StructureChangeExecutionDialog({
  houses,
  orchidGroups,
  operation,
  recordMode = false,
  closeAfterSubmit = true,
  active = true,
  embedded = false,
  recordNavigation,
  hiddenOtherVarietySourceIds = [],
  otherVarietyReferences = [],
  onRecordDirty,
  onResultRowsChange,
  onClose,
  onSaved,
  onSubmitRecord,
}: {
  houses: House[];
  orchidGroups: OrchidGroup[];
  operation: StructureChangeOperation;
  recordMode?: boolean;
  closeAfterSubmit?: boolean;
  active?: boolean;
  embedded?: boolean;
  recordNavigation?: {
    activeKey: string;
    allCompleted: boolean;
    items: WorkRecordNavigationItem[];
    saving: boolean;
    onSave: () => Promise<void>;
    onSelect: (key: string) => void;
  };
  hiddenOtherVarietySourceIds?: number[];
  otherVarietyReferences?: FarmPlacementReference[];
  onRecordDirty?: () => void;
  onResultRowsChange?: (rows: ResultRow[]) => void;
  onClose: () => void;
  onSaved?: (operation: WorkOperation) => void;
  onSubmitRecord?: (payload: StructureChangeExecutionPayload) => Promise<void>;
}) {
  const form = useStructureChangeExecution({
    closeAfterSubmit,
    onClose,
    onRecordDirty,
    onResultRowsChange,
    onSaved,
    onSubmitRecord,
    operation,
    orchidGroups,
    recordMode,
  });
  const movement = operation.workTypeCode === "MOVEMENT";
  const quantityDifference = form.totalResult - form.totalInput;
  const quantityDifferenceLabel = movement
    ? "폐기"
    : quantityDifference > 0
      ? "증식"
      : "손실";
  const quantityDifferenceValue = movement
    ? Math.max(0, -quantityDifference)
    : Math.abs(quantityDifference);
  const selectedGroupsById = new Map(
    form.selectedSources.map(({ group }) => [group.id, group]),
  );
  const movementBreakdown =
    movement && quantityDifferenceValue > 0
      ? allocateMovementQuantities(
          form.selectedSources.map(({ group }) => ({
            sourceOrchidGroupId: group.id,
            inputQuantity: Number(form.inputQuantities[group.id] || 0),
          })),
          form.totalResult,
        ).map((allocation) => ({
          group: selectedGroupsById.get(allocation.sourceOrchidGroupId)!,
          selectedQuantity: allocation.inputQuantity,
          movedQuantity: allocation.movedQuantity,
          discardQuantity: allocation.discardQuantity,
        }))
      : [];

  return (
    <div
      className={`${embedded ? "absolute" : "fixed z-[1300] bg-black/45"} inset-0 items-center justify-center p-4 ${
        active
          ? "pointer-events-auto visible flex"
          : "pointer-events-none invisible flex"
      }`}
      aria-hidden={!active}
      role="presentation"
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-5xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label={`${operation.workType} 실행 입력`}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-end justify-between border-b p-2">
          <div className="flex items-baseline gap-3">
            <h3 className="font-bold text-[#17251b]">
              {recordMode
                ? `${operation.workType} 작업 결과 입력`
                : `${operation.workType} 실행 회차 등록`}
            </h3>
            <p className="text-xs text-[#6a766e]">
              {movement
                ? "이동하지 않을 수량을 먼저 선별 폐기한 뒤, 남은 수량을 지정한 위치로 이동합니다."
                : recordMode
                  ? "선택한 모든 원본과 생성할 결과를 한 번에 입력하세요."
                  : "원본과 결과는 계획 대상에서 자동으로 채웠습니다. 이번 작업의 예외만 수정하세요."}
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>

        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4">
          <StructureChangeSourceFields
            availableSources={form.availableSources}
            houses={houses}
            inputQuantities={form.inputQuantities}
            recordMode={recordMode}
            releasedPlacements={form.releasedPlacements}
            selectedSourceIds={form.selectedSourceIds}
            totalInput={form.totalInput}
            onChangeInputQuantity={form.changeInputQuantity}
            onChangeReleasedPlacement={form.changeReleasedPlacement}
            onToggleSource={form.toggleSource}
          />

          <StructureChangeResultFields
            commonAgeYear={form.commonAgeYear}
            commonPotSize={form.commonPotSize}
            houses={houses}
            inputQuantities={form.inputQuantities}
            operation={operation}
            orchidGroups={orchidGroups}
            releasedPlacements={form.releasedPlacements}
            hiddenOtherVarietySourceIds={hiddenOtherVarietySourceIds}
            rows={form.rows}
            otherVarietyReferences={otherVarietyReferences}
            savedResultReferences={form.savedResultReferences}
            selectedSources={form.selectedSources}
            onAdd={form.addResult}
            onChange={form.patchRow}
            onRemove={form.removeResult}
            onSetAllAgeYears={form.setAllAgeYears}
            onSetAllPotSizes={form.setAllPotSizes}
          />

          <div className="grid gap-3 sm:grid-cols-2">
            <TextField
              label={recordMode ? "작업일" : "이번 실행 완료일"}
              max={form.today}
              required
              type="date"
              value={form.completedDate}
              onChange={form.setCompletedDate}
            />
            <div className="rounded-md bg-[#f4f7f3] px-3 py-2 text-sm text-[#526057]">
              {movement ? "선별 대상" : "투입"} {form.totalInput}분 ·{` `}
              {quantityDifferenceLabel} {quantityDifferenceValue}분 ·{` `}
              {movement ? "실제 이동" : "결과"} {form.totalResult}분 (자동 계산)
            </div>
            <TextField
              label="작업자"
              value={form.worker}
              onChange={form.setWorker}
            />
            <TextField label="메모" value={form.memo} onChange={form.setMemo} />
          </div>
          {movement && quantityDifferenceValue > 0 ? (
            <section className="rounded-md border border-[#e6cda0] bg-[#fff9eb] p-3 text-sm text-[#72531b] sm:col-span-2">
              <p className="font-bold">이동 후 남은 난은 폐기로 기록됩니다.</p>
              <p className="mt-1 text-xs leading-5">
                상태가 좋은 {form.totalResult}분을 먼저 이동하고, 원래 자리에
                남은 {quantityDifferenceValue}분을 원본별 선별 수량에 비례해
                자동 배분하여 폐기합니다. 두 작업은 하나의 처리 단위로 저장되며
                이동 작업을 취소할 때 함께 되돌립니다.
              </p>
              <ul className="mt-2 divide-y divide-[#ead9b6] border-t border-[#ead9b6] text-xs">
                {movementBreakdown.map((item) => (
                  <li
                    className="flex flex-wrap justify-between gap-2 py-1.5"
                    key={item.group.id}
                  >
                    <span>
                      {item.group.varietyName} ·{" "}
                      {sourceLocationLabel(item.group)}
                    </span>
                    <span className="font-semibold">
                      선별 {item.selectedQuantity} · 폐기 {item.discardQuantity}{" "}
                      · 이동 {item.movedQuantity}분
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
          {form.error ? (
            <p className="rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">
              {form.error}
            </p>
          ) : null}
        </div>

        <footer className="flex flex-wrap items-center justify-end gap-2 border-t p-2">
          {recordMode && recordNavigation ? (
            <WorkRecordVarietyNavigation
              activeKey={recordNavigation.activeKey}
              items={recordNavigation.items}
              onSelect={recordNavigation.onSelect}
            />
          ) : null}
          <button
            className={`${recordMode && recordNavigation ? "ml-2" : ""} rounded-md border px-4 py-2 text-sm`}
            type="button"
            onClick={onClose}
          >
            취소
          </button>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
            disabled={form.saving || form.availableSources.length === 0}
            type="button"
            onClick={() => void form.submit()}
          >
            {form.saving
              ? "처리 중"
              : recordMode && recordNavigation
                ? "현재 품종 입력 완료"
                : recordMode
                  ? "작업 기록 저장"
                  : "이번 실행 저장"}
          </button>
          {recordMode && recordNavigation ? (
            <button
              className="rounded-md bg-[#0f6f35] px-4 py-2 text-sm font-semibold text-white disabled:opacity-40"
              disabled={
                recordNavigation.saving || !recordNavigation.allCompleted
              }
              type="button"
              onClick={() => {
                void recordNavigation.onSave().catch((cause: unknown) => {
                  form.reportError(cause, "작업 기록을 저장하지 못했습니다.");
                });
              }}
            >
              {recordNavigation.saving ? "저장 중" : "전체 작업 기록 저장"}
            </button>
          ) : null}
        </footer>
      </section>
    </div>
  );
}
