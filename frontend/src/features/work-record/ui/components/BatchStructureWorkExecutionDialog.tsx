"use client";

import { Plus, Trash2, X } from "lucide-react";
import { useMemo, useState } from "react";
import type { House, OrchidGroup, WorkOperation } from "@/entities/farm/types";
import {
  FarmPlacementField,
  type FarmPlacementSelection,
} from "@/entities/farm/ui/FarmPlacementPicker";
import { POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";
import { createUuid } from "@/shared/lib/id";
import { executeStructureChangeWorkOperation } from "../../api/workRecordApi";
import { TextField } from "./FormFields";
import { localDateValue } from "./WorkCompletionDateDialog";

type ResultPurpose = "NORMAL" | "DIVIDE_CANDIDATE" | "HELD";

type ResultRow = {
  key: string;
  sourceOrchidGroupIds: number[];
  placement: FarmPlacementSelection | null;
  quantity: string;
  potSize: string;
  ageYear: string;
  purpose: ResultPurpose;
  autoQuantity: boolean;
};

export function BatchStructureWorkExecutionDialog({
  houses,
  orchidGroups,
  operation,
  onClose,
  onSaved,
}: {
  houses: House[];
  orchidGroups: OrchidGroup[];
  operation: WorkOperation;
  onClose: () => void;
  onSaved: (operation: WorkOperation) => void;
}) {
  const availableSources = useMemo(
    () =>
      operation.targets.flatMap((target) => {
        if (
          target.orchidGroupId == null ||
          target.remainingQuantity < 1 ||
          target.executionStatus === "SKIPPED" ||
          target.executionStatus === "CANCELED"
        ) {
          return [];
        }
        const group = orchidGroups.find(
          (candidate) => candidate.id === target.orchidGroupId,
        );
        if (!group) return [];
        return [
          {
            group,
            target,
            inferredQuantity: Math.min(
              group.quantity,
              target.remainingQuantity,
            ),
          },
        ];
      }),
    [operation.targets, orchidGroups],
  );
  const [selectedSourceIds, setSelectedSourceIds] = useState<Set<number>>(
    () => new Set(availableSources.map(({ group }) => group.id)),
  );
  const [inputQuantities, setInputQuantities] = useState<
    Record<number, string>
  >(() =>
    Object.fromEntries(
      availableSources.map(({ group, inferredQuantity }) => [
        group.id,
        String(inferredQuantity),
      ]),
    ),
  );
  const [rows, setRows] = useState<ResultRow[]>(() =>
    availableSources.map(({ group, inferredQuantity }) =>
      newResultRow(group, inferredQuantity),
    ),
  );
  const [lossQuantity, setLossQuantity] = useState("0");
  const [lossReason, setLossReason] = useState("");
  const today = localDateValue(new Date());
  const [completedDate, setCompletedDate] = useState(today);
  const [worker, setWorker] = useState(operation.worker ?? "");
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selectedSources = availableSources.filter(({ group }) =>
    selectedSourceIds.has(group.id),
  );
  const totalInput = selectedSources.reduce(
    (sum, { group }) => sum + Number(inputQuantities[group.id] || 0),
    0,
  );
  const totalResult = rows.reduce(
    (sum, row) => sum + Number(row.quantity || 0),
    0,
  );
  const commonPotSize =
    new Set(rows.map((row) => row.potSize)).size === 1
      ? (rows[0]?.potSize ?? "")
      : "";
  const commonAgeYear =
    new Set(rows.map((row) => row.ageYear)).size === 1
      ? (rows[0]?.ageYear ?? "")
      : "";

  function toggleSource(group: OrchidGroup) {
    const selected = selectedSourceIds.has(group.id);
    setSelectedSourceIds((current) => {
      const next = new Set(current);
      if (selected) next.delete(group.id);
      else next.add(group.id);
      return next;
    });
    if (selected) {
      setRows((current) =>
        current.filter(
          (row) =>
            row.sourceOrchidGroupIds.length !== 1 ||
            row.sourceOrchidGroupIds[0] !== group.id,
        ),
      );
    } else {
      const quantity = Number(inputQuantities[group.id] || group.quantity);
      setRows((current) => [...current, newResultRow(group, quantity)]);
    }
  }

  function changeInputQuantity(groupId: number, value: string) {
    setInputQuantities((current) => ({ ...current, [groupId]: value }));
    setRows((current) =>
      current.map((row) =>
        row.autoQuantity &&
        row.sourceOrchidGroupIds.length === 1 &&
        row.sourceOrchidGroupIds[0] === groupId
          ? { ...row, quantity: value }
          : row,
      ),
    );
  }

  function changeLossQuantity(value: string) {
    const delta = Number(value || 0) - Number(lossQuantity || 0);
    if (Number.isInteger(delta) && delta !== 0) {
      setRows((current) => {
        const donor = [...current]
          .filter((row) => row.autoQuantity)
          .sort(
            (left, right) => Number(right.quantity) - Number(left.quantity),
          )[0];
        if (!donor || Number(donor.quantity) - delta < 1) return current;
        return current.map((row) =>
          row.key === donor.key
            ? { ...row, quantity: String(Number(row.quantity) - delta) }
            : row,
        );
      });
    }
    setLossQuantity(value);
  }

  function patchRow(key: string, patch: Partial<ResultRow>) {
    setRows((current) =>
      current.map((row) => (row.key === key ? { ...row, ...patch } : row)),
    );
  }

  function addResult() {
    const donor = [...rows]
      .filter((row) => Number(row.quantity) > 1)
      .sort((left, right) => Number(right.quantity) - Number(left.quantity))[0];
    const sourceId = donor?.sourceOrchidGroupIds[0];
    const source =
      availableSources.find(({ group }) => group.id === sourceId)?.group ??
      selectedSources[0]?.group;
    if (!source) return;
    setRows((current) => [
      ...current.map((row) =>
        row.key === donor?.key
          ? { ...row, quantity: String(Number(row.quantity) - 1) }
          : row,
      ),
      {
        ...newResultRow(source, 1),
        placement: null,
        sourceOrchidGroupIds: donor?.sourceOrchidGroupIds ?? [source.id],
        autoQuantity: false,
      },
    ]);
  }

  function removeResult(removed: ResultRow) {
    setRows((current) => {
      const next = current.filter((row) => row.key !== removed.key);
      const receiverIndex = next.findIndex((row) =>
        row.sourceOrchidGroupIds.some((id) =>
          removed.sourceOrchidGroupIds.includes(id),
        ),
      );
      if (receiverIndex >= 0) {
        next[receiverIndex] = {
          ...next[receiverIndex],
          sourceOrchidGroupIds: [
            ...new Set([
              ...next[receiverIndex].sourceOrchidGroupIds,
              ...removed.sourceOrchidGroupIds,
            ]),
          ],
          quantity: String(
            Number(next[receiverIndex].quantity) + Number(removed.quantity),
          ),
        };
      }
      return next;
    });
  }

  async function submit() {
    const validation = validate();
    if (validation) {
      setError(validation);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await executeStructureChangeWorkOperation(operation.id, {
        idempotencyKey: createUuid(),
        completedDate,
        worker: worker.trim() || null,
        memo: memo.trim() || null,
        sources: selectedSources.map(({ group }) => ({
          sourceOrchidGroupId: group.id,
          inputQuantity: Number(inputQuantities[group.id]),
        })),
        lossQuantity: Number(lossQuantity),
        lossReason: Number(lossQuantity) > 0 ? lossReason.trim() : null,
        results: rows.map((row) => ({
          bedZoneId: row.placement!.bedZoneId,
          quantity: Number(row.quantity),
          sourceOrchidGroupIds: row.sourceOrchidGroupIds,
          potSize: row.potSize || null,
          ageYear: row.ageYear ? Number(row.ageYear) : null,
          purpose: row.purpose,
          placementType: null,
          trayCount: null,
          splitPlacementAllowed: false,
          startPosition: row.placement!.startPosition,
          endPosition: row.placement!.endPosition,
          memo: null,
        })),
      });
      onSaved(updated);
      onClose();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : `${operation.workType} 작업을 실행하지 못했습니다.`,
      );
    } finally {
      setSaving(false);
    }
  }

  function validate() {
    if (!completedDate) return "완료일을 입력해주세요.";
    if (selectedSources.length === 0)
      return "이번에 작업할 원본을 선택해주세요.";
    if (
      selectedSources.some(({ group, target }) => {
        const quantity = Number(inputQuantities[group.id]);
        return (
          !Number.isInteger(quantity) ||
          quantity < 1 ||
          quantity > group.quantity ||
          quantity > target.remainingQuantity
        );
      })
    ) {
      return "작업 수량은 현재 수량과 계획 잔여 수량 이내로 입력해주세요.";
    }
    const loss = Number(lossQuantity);
    if (!Number.isInteger(loss) || loss < 0) return "손실 수량을 확인해주세요.";
    if (totalInput !== totalResult + loss)
      return "투입 수량과 결과·손실 수량 합계가 맞지 않습니다.";
    if (loss > 0 && !lossReason.trim()) return "손실 사유를 입력해주세요.";
    if (
      rows.length === 0 ||
      rows.some(
        (row) =>
          !row.placement ||
          !Number.isInteger(Number(row.quantity)) ||
          Number(row.quantity) < 1 ||
          row.sourceOrchidGroupIds.length === 0 ||
          row.sourceOrchidGroupIds.some((id) => !selectedSourceIds.has(id)),
      )
    ) {
      return "결과 난 묶음의 원본·위치·수량을 확인해주세요.";
    }
    if (hasOverlappingPlacements(rows))
      return "결과 난 묶음끼리 배치 칸이 겹칩니다.";
    for (const { group } of selectedSources) {
      if (
        Number(inputQuantities[group.id]) < group.quantity &&
        overlapsSource(
          rows.filter((row) => row.sourceOrchidGroupIds.includes(group.id)),
          group,
        )
      ) {
        return "일부만 작업하는 원본의 잔여 배치와 결과 배치가 겹칠 수 없습니다.";
      }
    }
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-[1300] flex items-center justify-center bg-black/45 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-5xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label={`${operation.workType} 실행 입력`}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between border-b p-4">
          <div>
            <h3 className="font-bold text-[#17251b]">
              {operation.workType} 실행 회차 등록
            </h3>
            <p className="mt-1 text-xs text-[#6a766e]">
              원본과 결과는 계획 대상에서 자동으로 채웠습니다. 이번 작업의
              예외만 수정하세요.
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>

        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4">
          <section className="rounded-md border border-[#cfe0d2] bg-[#f7faf6] p-3">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-sm font-bold text-[#26352b]">
                  이번 실행 원본
                </p>
                <p className="mt-0.5 text-xs text-[#6a766e]">
                  계획 잔여 수량을 기본 작업 수량으로 선택했습니다.
                </p>
              </div>
              <span className="text-xs font-semibold text-[#10783a]">
                {selectedSources.length}묶음 · {totalInput}분
              </span>
            </div>
            <div className="mt-3 grid gap-2 md:grid-cols-2">
              {availableSources.map(({ group, target }) => (
                <label
                  className="grid cursor-pointer items-center gap-2 rounded-md bg-white p-2 sm:grid-cols-[auto_minmax(0,1fr)_120px]"
                  key={group.id}
                >
                  <input
                    className="h-4 w-4 accent-[#159447]"
                    checked={selectedSourceIds.has(group.id)}
                    type="checkbox"
                    onChange={() => toggleSource(group)}
                  />
                  <span className="min-w-0 text-xs">
                    <span className="block truncate font-semibold text-[#26352b]">
                      {group.varietyName} · {group.houseNumber}동{" "}
                      {group.physicalBedNumber}다이
                    </span>
                    <span className="text-[#6a766e]">
                      현재 {group.quantity}분 · 계획 잔여{" "}
                      {target.remainingQuantity}분
                    </span>
                  </span>
                  <TextField
                    label="작업 수량"
                    type="number"
                    value={inputQuantities[group.id] ?? ""}
                    onChange={(value) => changeInputQuantity(group.id, value)}
                  />
                </label>
              ))}
            </div>
          </section>

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
                onClick={addResult}
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
                  onChange={(event) =>
                    setRows((current) =>
                      current.map((row) => ({
                        ...row,
                        potSize: event.target.value,
                      })),
                    )
                  }
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
                label="전체 결과 년생"
                type="number"
                value={commonAgeYear}
                onChange={(ageYear) =>
                  setRows((current) =>
                    current.map((row) => ({ ...row, ageYear })),
                  )
                }
              />
            </div>
            {rows.map((row, index) => (
              <ResultFields
                excludeOrchidGroupId={
                  row.sourceOrchidGroupIds.length === 1 &&
                  Number(inputQuantities[row.sourceOrchidGroupIds[0]]) ===
                    orchidGroups.find(
                      (group) => group.id === row.sourceOrchidGroupIds[0],
                    )?.quantity
                    ? row.sourceOrchidGroupIds[0]
                    : null
                }
                houses={houses}
                index={index}
                key={row.key}
                operation={operation}
                removable={rows.length > 1}
                row={row}
                onChange={(patch) => patchRow(row.key, patch)}
                onRemove={() => removeResult(row)}
              />
            ))}
          </section>

          <div className="grid gap-3 sm:grid-cols-2">
            <TextField
              label="이번 실행 완료일"
              max={today}
              required
              type="date"
              value={completedDate}
              onChange={setCompletedDate}
            />
            <TextField
              label="손실 수량"
              type="number"
              value={lossQuantity}
              onChange={changeLossQuantity}
            />
            {Number(lossQuantity) > 0 ? (
              <TextField
                label="손실 사유"
                value={lossReason}
                onChange={setLossReason}
              />
            ) : (
              <div className="rounded-md bg-[#f4f7f3] px-3 py-2 text-sm text-[#526057]">
                투입 {totalInput}분 · 결과 {totalResult}분
              </div>
            )}
            <TextField label="작업자" value={worker} onChange={setWorker} />
            <TextField label="메모" value={memo} onChange={setMemo} />
          </div>
          {error ? (
            <p className="rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">
              {error}
            </p>
          ) : null}
        </div>

        <footer className="flex justify-end gap-2 border-t p-4">
          <button
            className="rounded-md border px-4 py-2 text-sm"
            type="button"
            onClick={onClose}
          >
            취소
          </button>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
            disabled={saving || availableSources.length === 0}
            type="button"
            onClick={() => void submit()}
          >
            {saving ? "처리 중" : "이번 실행 저장"}
          </button>
        </footer>
      </section>
    </div>
  );
}

function ResultFields({
  excludeOrchidGroupId,
  houses,
  index,
  operation,
  removable,
  row,
  onChange,
  onRemove,
}: {
  excludeOrchidGroupId: number | null;
  houses: House[];
  index: number;
  operation: WorkOperation;
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
            excludeOrchidGroupId={excludeOrchidGroupId}
            houses={houses}
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
          label="년생"
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

function newResultRow(group: OrchidGroup, quantity: number): ResultRow {
  return {
    key: createUuid(),
    sourceOrchidGroupIds: [group.id],
    placement: inferPlacement(group),
    quantity: String(quantity),
    potSize: group.potSize ?? "",
    ageYear: group.ageYear == null ? "" : String(group.ageYear),
    purpose: "NORMAL",
    autoQuantity: true,
  };
}

function inferPlacement(group: OrchidGroup): FarmPlacementSelection | null {
  if (group.startPosition == null || group.endPosition == null) return null;
  return {
    bedZoneId: group.bedZoneId,
    startCell: Math.floor(group.startPosition) + 1,
    endCell: Math.ceil(group.endPosition),
    startPosition: group.startPosition,
    endPosition: group.endPosition,
    label: `${group.houseNumber}동 ${group.physicalBedNumber}다이 ${group.bedZoneName} · ${Math.floor(group.startPosition) + 1}~${Math.ceil(group.endPosition)}칸`,
  };
}

function hasOverlappingPlacements(rows: ResultRow[]) {
  return rows.some((left, index) =>
    rows
      .slice(index + 1)
      .some((right) =>
        Boolean(
          left.placement &&
          right.placement &&
          left.placement.bedZoneId === right.placement.bedZoneId &&
          left.placement.startPosition < right.placement.endPosition &&
          right.placement.startPosition < left.placement.endPosition,
        ),
      ),
  );
}

function overlapsSource(rows: ResultRow[], source: OrchidGroup) {
  if (source.startPosition == null || source.endPosition == null) return false;
  return rows.some(
    (row) =>
      row.placement &&
      row.placement.bedZoneId === source.bedZoneId &&
      row.placement.startPosition < source.endPosition! &&
      source.startPosition! < row.placement.endPosition,
  );
}
