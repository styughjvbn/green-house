"use client";

import { useState } from "react";
import { Plus, Trash2, X } from "lucide-react";
import type {
  BedZone,
  House,
  OrchidGroup,
  WorkOperation,
  WorkOperationTarget,
} from "@/entities/farm/types";
import {
  PottingExecutionForm,
  type PottingExecutionValues,
} from "@/entities/farm/ui/PottingExecutionForm";
import {
  FarmPlacementField,
  type FarmPlacementSelection,
} from "@/entities/farm/ui/FarmPlacementPicker";
import { POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";
import { createUuid } from "@/shared/lib/id";
import {
  completeMergeWorkOperation,
  transitionWorkOperationTarget,
} from "../../api/workRecordApi";
import { BatchStructureWorkExecutionDialog } from "./BatchStructureWorkExecutionDialog";
import { TextField } from "./FormFields";

type ResultRow = {
  key: string;
  placement: FarmPlacementSelection | null;
  quantity: string;
  potSize: string;
  ageYear: string;
};

type StructureWorkExecutionDialogProps = {
  bedZones: BedZone[];
  houses: House[];
  orchidGroups: OrchidGroup[];
  operation: WorkOperation;
  source: OrchidGroup | null;
  target: WorkOperationTarget;
  onClose: () => void;
  onSaved: (operation: WorkOperation) => void;
};

export function StructureWorkExecutionDialog(
  props: StructureWorkExecutionDialogProps,
) {
  if (props.operation.workTypeCode === "DISCARD") {
    return <DiscardWorkExecutionDialog {...props} />;
  }
  if (props.operation.workTypeCode === "POTTING") {
    return <PottingWorkExecutionDialog {...props} />;
  }
  if (
    props.operation.workTypeCode === "REPOT" ||
    props.operation.workTypeCode === "DIVIDE" ||
    props.operation.workTypeCode === "MERGE"
  ) {
    return <BatchStructureWorkExecutionDialog {...props} />;
  }
  return <OrchidStructureWorkExecutionDialog {...props} />;
}

function DiscardWorkExecutionDialog({
  operation,
  source,
  target,
  onClose,
  onSaved,
}: StructureWorkExecutionDialogProps) {
  const maximumQuantity = Math.min(
    source?.quantity ?? target.remainingQuantity,
    target.remainingQuantity,
  );
  const [discardQuantity, setDiscardQuantity] = useState(
    String(maximumQuantity),
  );
  const [worker, setWorker] = useState(operation.worker ?? "");
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (target.id == null) return;
    const quantity = Number(discardQuantity);
    if (
      !Number.isInteger(quantity) ||
      quantity < 1 ||
      quantity > maximumQuantity
    ) {
      setError(`폐기 수량은 1 이상 ${maximumQuantity} 이하로 입력해주세요.`);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onSaved(
        await transitionWorkOperationTarget(
          operation.id,
          target.id,
          "complete",
          worker.trim() || null,
          {
            discardQuantity: quantity,
            reason: reason.trim() || null,
          },
        ),
      );
      onClose();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "폐기 작업을 실행하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-[1300] flex items-center justify-center bg-black/45 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="w-full max-w-lg rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label="폐기 실행 입력"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between border-b p-4">
          <div>
            <p className="text-sm font-semibold text-[#9b341e]">폐기 실행</p>
            <h2 className="mt-1 text-xl font-semibold text-[#17251b]">
              {source?.varietyName ?? target.varietyName}
            </h2>
            <p className="mt-1 text-sm text-[#5c6a60]">
              현재 {source?.quantity ?? target.quantitySnapshot}분 · 작업 잔여{" "}
              {target.remainingQuantity}분
            </p>
          </div>
          <button
            className="flex h-8 w-8 items-center justify-center rounded border border-[#d9dfda]"
            type="button"
            onClick={onClose}
            aria-label="닫기"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>

        <div className="space-y-4 p-4">
          <div className="grid grid-cols-[minmax(0,1fr)_auto] items-end gap-2">
            <TextField
              label="폐기 수량"
              required
              type="number"
              value={discardQuantity}
              onChange={setDiscardQuantity}
            />
            <button
              className="h-[38px] rounded-md border border-[#c25a3c] px-3 text-sm font-semibold text-[#9b341e] hover:bg-[#fff1ec]"
              type="button"
              onClick={() => setDiscardQuantity(String(maximumQuantity))}
            >
              전량 입력
            </button>
          </div>
          <TextField label="작업자" value={worker} onChange={setWorker} />
          <label className="block">
            <span className="text-sm font-semibold text-[#435047]">
              폐기 사유
            </span>
            <textarea
              className="mt-1 min-h-24 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          <p className="rounded-md bg-[#fff7ed] p-3 text-sm text-[#8a4b16]">
            일부 폐기하면 잔여 수량을 유지하고, 전량 폐기하면 난 묶음 상태가
            폐기로 변경됩니다.
          </p>
          {error ? (
            <p className="rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">
              {error}
            </p>
          ) : null}
        </div>

        <footer className="flex justify-end gap-2 border-t p-4">
          <button
            className="rounded-md border border-[#cfd8cc] px-4 py-2 text-sm font-semibold"
            type="button"
            onClick={onClose}
          >
            취소
          </button>
          <button
            className="rounded-md bg-[#b5472f] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
            disabled={saving}
            type="button"
            onClick={() => void submit()}
          >
            {saving ? "처리 중" : "폐기 실행"}
          </button>
        </footer>
      </section>
    </div>
  );
}

function OrchidStructureWorkExecutionDialog({
  bedZones,
  houses,
  operation,
  source,
  target,
  onClose,
  onSaved,
}: StructureWorkExecutionDialogProps) {
  const isRepot =
    operation.workTypeCode === "REPOT" || operation.workTypeCode === "DIVIDE";
  const isMovement = operation.workTypeCode === "MOVEMENT";
  const initialBedZoneId =
    (isMovement
      ? bedZones.find((zone) => zone.id !== target.locationSnapshot.bedZoneId)
          ?.id
      : bedZones[0]?.id) ?? bedZones[0]?.id;
  const [worker, setWorker] = useState(operation.worker ?? "");
  const [memo, setMemo] = useState("");
  const [inputQuantity, setInputQuantity] = useState(
    String(source?.quantity ?? target.quantitySnapshot),
  );
  const [lossQuantity, setLossQuantity] = useState("0");
  const [lossReason, setLossReason] = useState("");
  const [bedZoneId, setBedZoneId] = useState(String(initialBedZoneId ?? ""));
  const [startCell, setStartCell] = useState("1");
  const [endCell, setEndCell] = useState("1");
  const [rows, setRows] = useState<ResultRow[]>(() => [newResultRow(source)]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (target.id == null) return;
    const validation = validate();
    if (validation) {
      setError(validation);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const resultDetails = isMovement
        ? {
            toBedZoneId: Number(bedZoneId),
            startPosition: Number(startCell) - 1,
            endPosition: Number(endCell),
            worker: worker.trim() || null,
            memo: memo.trim() || null,
          }
        : {
            idempotencyKey: createUuid(),
            title: operation.title,
            workDate: new Date().toISOString().slice(0, 10),
            worker: worker.trim() || null,
            memo: memo.trim() || null,
            sourceOrchidGroupId: target.orchidGroupId,
            inputQuantity: Number(inputQuantity),
            lossQuantity: Number(lossQuantity),
            lossReason: Number(lossQuantity) > 0 ? lossReason.trim() : null,
            inheritCollectionIds: [],
            results: rows.map((row) => ({
              bedZoneId: row.placement!.bedZoneId,
              quantity: Number(row.quantity),
              potSize: row.potSize || null,
              ageYear: row.ageYear ? Number(row.ageYear) : null,
              placementType: null,
              trayCount: null,
              splitPlacementAllowed: false,
              startPosition: row.placement!.startPosition,
              endPosition: row.placement!.endPosition,
              memo: null,
            })),
          };
      onSaved(
        await transitionWorkOperationTarget(
          operation.id,
          target.id,
          "complete",
          worker.trim() || null,
          resultDetails,
        ),
      );
      onClose();
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : "작업을 실행하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  function validate() {
    if (!bedZones.length) return "결과를 배치할 논리 구역이 없습니다.";
    if (isMovement) {
      if (!source) return "현재 원본 난 묶음을 찾을 수 없습니다.";
      if (!bedZoneId) return "이동할 구역을 선택해주세요.";
      if (Number(startCell) < 1 || Number(endCell) < Number(startCell))
        return "이동 시작·끝 칸을 확인해주세요.";
      const startPosition = Number(startCell) - 1;
      const endPosition = Number(endCell);
      if (
        Number(bedZoneId) === source.bedZoneId &&
        startPosition === source.startPosition &&
        endPosition === source.endPosition
      )
        return "현재 위치와 다른 이동 위치를 입력해주세요.";
      return null;
    }
    if (isRepot) {
      if (!source) return "현재 원본 난 묶음을 찾을 수 없습니다.";
      const input = Number(inputQuantity);
      const loss = Number(lossQuantity);
      const resultTotal = rows.reduce(
        (sum, row) => sum + Number(row.quantity || 0),
        0,
      );
      if (!Number.isInteger(input) || input < 1 || input > source.quantity)
        return `투입 수량은 1 이상 ${source.quantity} 이하로 입력해주세요.`;
      if (!Number.isInteger(loss) || loss < 0)
        return "손실 수량을 확인해주세요.";
      if (input !== resultTotal + loss)
        return "투입 수량과 결과 수량 합계 및 손실 수량이 맞지 않습니다.";
      if (loss > 0 && !lossReason.trim()) return "손실 사유를 입력해주세요.";
      if (
        rows.some(
          (row) =>
            !row.placement ||
            !Number.isInteger(Number(row.quantity)) ||
            Number(row.quantity) < 1,
        )
      )
        return "결과 난 묶음의 위치와 수량을 확인해주세요.";
      if (input < source.quantity && hasSourcePlacementOverlap(rows, source))
        return "부분 분갈이 시 결과 배치는 남은 원본 난 묶음과 겹칠 수 없습니다.";
      if (hasOverlappingResultPlacements(rows))
        return "결과 난 묶음끼리 배치 칸이 겹칩니다.";
      return null;
    }
    return "지원하지 않는 작업 유형입니다.";
  }

  return (
    <div
      className="fixed inset-0 z-[1300] flex items-center justify-center bg-black/45 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-3xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label={`${operation.workType} 실행 입력`}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between border-b p-4">
          <div>
            <h3 className="font-bold text-[#17251b]">
              {operation.workType} 실행 입력
            </h3>
            <p className="mt-1 text-xs text-[#6a766e]">
              {target.varietyName} · 계획 #{operation.id}
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>
        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4">
          {isRepot ? (
            <>
              <div className="grid gap-3 sm:grid-cols-2">
                <TextField
                  label="투입 수량"
                  type="number"
                  value={inputQuantity}
                  onChange={setInputQuantity}
                />
                <TextField
                  label="손실 수량"
                  type="number"
                  value={lossQuantity}
                  onChange={setLossQuantity}
                />
              </div>
              {Number(lossQuantity) > 0 ? (
                <TextField
                  label="손실 사유"
                  value={lossReason}
                  onChange={setLossReason}
                />
              ) : null}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-bold">결과 난 묶음</p>
                  <button
                    className="inline-flex items-center gap-1 rounded border px-2 py-1 text-xs"
                    type="button"
                    onClick={() =>
                      setRows((current) => [...current, newResultRow(source)])
                    }
                  >
                    <Plus className="h-3 w-3" aria-hidden="true" /> 추가
                  </button>
                </div>
                {rows.map((row, index) => (
                  <ResultRowFields
                    key={row.key}
                    excludeOrchidGroupId={
                      source && Number(inputQuantity) === source.quantity
                        ? source.id
                        : null
                    }
                    houses={houses}
                    index={index}
                    row={row}
                    removable={rows.length > 1}
                    onChange={(patch) =>
                      setRows((current) =>
                        current.map((candidate) =>
                          candidate.key === row.key
                            ? { ...candidate, ...patch }
                            : candidate,
                        ),
                      )
                    }
                    onRemove={() =>
                      setRows((current) =>
                        current.filter(
                          (candidate) => candidate.key !== row.key,
                        ),
                      )
                    }
                  />
                ))}
              </div>
            </>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              <SelectInput
                label="이동할 구역"
                value={bedZoneId}
                onChange={setBedZoneId}
                options={bedZones.map((zone) => ({
                  value: String(zone.id),
                  label: `${zone.houseNumber}동 ${zone.physicalBedNumber}다이 ${zone.name}`,
                }))}
              />
              <div className="rounded-md border border-[#dfe5dc] bg-[#f8faf7] px-3 py-2 text-sm text-[#526057]">
                <span className="block text-xs font-semibold">현재 위치</span>
                <span className="mt-1 block">
                  {target.locationSnapshot.houseNumber}동{" "}
                  {target.locationSnapshot.physicalBedNumber}다이{" "}
                  {target.locationSnapshot.bedZoneName}
                </span>
              </div>
              <TextField
                label="시작 칸"
                type="number"
                value={startCell}
                onChange={setStartCell}
              />
              <TextField
                label="끝 칸"
                type="number"
                value={endCell}
                onChange={setEndCell}
              />
            </div>
          )}
          <div className="grid gap-3 sm:grid-cols-2">
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
            disabled={saving}
            type="button"
            onClick={submit}
          >
            {saving ? "처리 중" : "작업 완료"}
          </button>
        </footer>
      </section>
    </div>
  );
}

function MergeWorkExecutionDialog({
  houses,
  orchidGroups,
  operation,
  onClose,
  onSaved,
}: StructureWorkExecutionDialogProps) {
  const sources = operation.targets
    .map((target) => {
      const group = orchidGroups.find(
        (candidate) => candidate.id === target.orchidGroupId,
      );
      return group ? { group, target } : null;
    })
    .filter((item): item is NonNullable<typeof item> => item != null);
  const [inputQuantities, setInputQuantities] = useState<
    Record<number, string>
  >(() =>
    Object.fromEntries(
      sources.map(({ group }) => [group.id, String(group.quantity)]),
    ),
  );
  const initialTotal = sources.reduce(
    (sum, { group }) => sum + group.quantity,
    0,
  );
  const [lossQuantity, setLossQuantity] = useState("0");
  const [lossReason, setLossReason] = useState("");
  const [resultQuantity, setResultQuantity] = useState(String(initialTotal));
  const [placement, setPlacement] = useState<FarmPlacementSelection | null>(
    null,
  );
  const [potSize, setPotSize] = useState(sources[0]?.group.potSize ?? "");
  const [ageYear, setAgeYear] = useState(
    sources[0]?.group.ageYear == null ? "" : String(sources[0].group.ageYear),
  );
  const [worker, setWorker] = useState(operation.worker ?? "");
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    const validation = validate();
    if (validation) {
      setError(validation);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await completeMergeWorkOperation(
        operation.id,
        worker.trim() || null,
        {
          sources: sources.map(({ group }) => ({
            sourceOrchidGroupId: group.id,
            inputQuantity: Number(inputQuantities[group.id]),
          })),
          lossQuantity: Number(lossQuantity),
          lossReason: Number(lossQuantity) > 0 ? lossReason.trim() : null,
          result: {
            bedZoneId: placement!.bedZoneId,
            quantity: Number(resultQuantity),
            potSize: potSize || null,
            ageYear: ageYear ? Number(ageYear) : null,
            placementType: null,
            trayCount: null,
            splitPlacementAllowed: false,
            startPosition: placement!.startPosition,
            endPosition: placement!.endPosition,
            memo: memo.trim() || null,
          },
        },
      );
      onSaved(updated);
      onClose();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "합식 작업을 실행하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  function validate() {
    if (sources.length !== operation.targets.length || sources.length < 2)
      return "합식할 현재 원본 난 묶음을 모두 찾을 수 없습니다.";
    if (
      sources.some(({ group }) => {
        const quantity = Number(inputQuantities[group.id]);
        return (
          !Number.isInteger(quantity) ||
          quantity < 1 ||
          quantity > group.quantity
        );
      })
    )
      return "각 원본의 투입 수량을 현재 수량 이하로 입력해주세요.";
    const totalInput = sources.reduce(
      (sum, { group }) => sum + Number(inputQuantities[group.id]),
      0,
    );
    const result = Number(resultQuantity);
    const loss = Number(lossQuantity);
    if (!Number.isInteger(result) || result < 1)
      return "결과 수량을 1 이상으로 입력해주세요.";
    if (!Number.isInteger(loss) || loss < 0) return "손실 수량을 확인해주세요.";
    if (totalInput !== result + loss)
      return "원본 투입 합계와 결과·손실 수량이 맞지 않습니다.";
    if (loss > 0 && !lossReason.trim())
      return "손실 수량이 있으면 손실 사유를 입력해주세요.";
    if (!placement) return "합식 결과를 배치할 위치를 선택해주세요.";
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-[1300] flex items-center justify-center bg-black/45 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-3xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label="합식 실행 입력"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between border-b p-4">
          <div>
            <h3 className="font-bold text-[#17251b]">합식 실행 입력</h3>
            <p className="mt-1 text-xs text-[#6a766e]">
              원본 {sources.length}묶음 · 계획 #{operation.id}
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>
        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4">
          <section className="space-y-2 rounded-md border bg-[#f8faf7] p-3">
            <p className="text-sm font-bold">원본별 투입 수량</p>
            {sources.map(({ group }) => (
              <div
                key={group.id}
                className="grid items-center gap-2 rounded-md bg-white p-2 sm:grid-cols-[minmax(0,1fr)_140px]"
              >
                <div className="min-w-0 text-sm">
                  <p className="truncate font-semibold">{group.varietyName}</p>
                  <p className="text-xs text-[#6a766e]">
                    {group.houseNumber}동 {group.physicalBedNumber}다이{" "}
                    {group.bedZoneName} · 현재 {group.quantity}분
                  </p>
                </div>
                <TextField
                  label="투입 수량"
                  type="number"
                  value={inputQuantities[group.id] ?? ""}
                  onChange={(value) =>
                    setInputQuantities((current) => ({
                      ...current,
                      [group.id]: value,
                    }))
                  }
                />
              </div>
            ))}
          </section>
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField
              label="결과 수량"
              type="number"
              value={resultQuantity}
              onChange={setResultQuantity}
            />
            <TextField
              label="손실 수량"
              type="number"
              value={lossQuantity}
              onChange={setLossQuantity}
            />
            {Number(lossQuantity) > 0 ? (
              <TextField
                label="손실 사유"
                value={lossReason}
                onChange={setLossReason}
              />
            ) : null}
            <PotSizeInput value={potSize} onChange={setPotSize} />
            <TextField
              label="년생"
              type="number"
              value={ageYear}
              onChange={setAgeYear}
            />
          </div>
          <FarmPlacementField
            dialogDescription="합식 결과 난 묶음이 차지할 구역과 시작·끝 칸을 선택하세요."
            dialogTitle="합식 결과 배치 위치"
            houses={houses}
            value={placement}
            onChange={setPlacement}
          />
          <div className="grid gap-3 sm:grid-cols-2">
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
            disabled={saving}
            type="button"
            onClick={onClose}
          >
            취소
          </button>
          <button
            className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
            disabled={saving}
            type="button"
            onClick={() => void submit()}
          >
            {saving ? "처리 중" : "합식 완료"}
          </button>
        </footer>
      </section>
    </div>
  );
}

function PottingWorkExecutionDialog({
  houses,
  operation,
  target,
  onClose,
  onSaved,
}: StructureWorkExecutionDialogProps) {
  async function complete(values: PottingExecutionValues) {
    if (target.id == null) {
      throw new Error("포트 작업 대상을 찾을 수 없습니다.");
    }
    const updated = await transitionWorkOperationTarget(
      operation.id,
      target.id,
      "complete",
      values.worker ?? null,
      {
        ...values,
        growthStage: null,
      },
    );
    onSaved(updated);
    onClose();
  }

  return (
    <div
      className="fixed inset-0 z-[1300] flex items-center justify-center bg-black/45 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="max-h-[calc(100dvh-2rem)] w-full max-w-3xl overflow-y-auto rounded-lg bg-white p-5 shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label="포트 작업 실행 입력"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between gap-3 border-b pb-4">
          <div>
            <h3 className="font-bold text-[#17251b]">포트 작업 실행 입력</h3>
            <p className="mt-1 text-xs text-[#6a766e]">
              {target.varietyName} · 계획 #{operation.id}
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>
        <PottingExecutionForm
          houses={houses}
          initialActualQuantity={Math.max(1, target.quantitySnapshot)}
          initialAgeYear={target.ageYearSnapshot}
          initialPotSize={target.potSizeSnapshot}
          initialWorker={operation.worker}
          subject={target.varietyName}
          onCancel={onClose}
          onSubmit={complete}
        />
      </section>
    </div>
  );
}

function ResultRowFields({
  excludeOrchidGroupId,
  houses,
  index,
  row,
  removable,
  onChange,
  onRemove,
}: {
  excludeOrchidGroupId: number | null;
  houses: House[];
  index: number;
  row: ResultRow;
  removable: boolean;
  onChange: (patch: Partial<ResultRow>) => void;
  onRemove: () => void;
}) {
  return (
    <section className="rounded-md border bg-[#f8faf7] p-3">
      <div className="mb-2 flex items-center justify-between">
        <p className="text-xs font-bold">결과 {index + 1}</p>
        {removable ? (
          <button type="button" aria-label="결과 삭제" onClick={onRemove}>
            <Trash2 className="h-4 w-4 text-[#a33a24]" aria-hidden="true" />
          </button>
        ) : null}
      </div>
      <div className="grid gap-2 sm:grid-cols-3">
        <div className="sm:col-span-3">
          <FarmPlacementField
            dialogDescription="구역을 고른 뒤 분갈이 결과 난 묶음이 차지할 시작 칸과 끝 칸을 지정하세요."
            dialogTitle={`분갈이 결과 ${index + 1} 배치 위치`}
            excludeOrchidGroupId={excludeOrchidGroupId}
            fieldLabel="배치 위치"
            houses={houses}
            value={row.placement}
            onChange={(placement) => onChange({ placement })}
          />
        </div>
        <TextField
          label="결과 수량"
          type="number"
          value={row.quantity}
          onChange={(value) => onChange({ quantity: value })}
        />
        <PotSizeInput
          value={row.potSize}
          onChange={(value) => onChange({ potSize: value })}
        />
        <TextField
          label="년생"
          type="number"
          value={row.ageYear}
          onChange={(value) => onChange({ ageYear: value })}
        />
      </div>
    </section>
  );
}

function SelectInput({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="block text-sm font-semibold text-[#435047]">
      {label}
      <select
        className="mt-1 w-full rounded-md border border-[#cfd8cc] bg-white px-2 py-2 font-normal"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}

function PotSizeInput({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <SelectInput
      label="화분 크기"
      value={value}
      onChange={onChange}
      options={POT_SIZE_OPTIONS.map((option) => ({
        value: option.value,
        label: option.label,
      }))}
    />
  );
}

function newResultRow(source: OrchidGroup | null): ResultRow {
  return {
    key: createUuid(),
    placement: null,
    quantity: String(source?.quantity ?? 1),
    potSize: source?.potSize ?? "",
    ageYear: source?.ageYear == null ? "" : String(source.ageYear),
  };
}

function hasOverlappingResultPlacements(rows: ResultRow[]) {
  for (let leftIndex = 0; leftIndex < rows.length; leftIndex += 1) {
    const left = rows[leftIndex].placement;
    if (!left) continue;
    for (
      let rightIndex = leftIndex + 1;
      rightIndex < rows.length;
      rightIndex += 1
    ) {
      const right = rows[rightIndex].placement;
      if (!right || left.bedZoneId !== right.bedZoneId) continue;
      if (
        left.startPosition < right.endPosition &&
        right.startPosition < left.endPosition
      ) {
        return true;
      }
    }
  }
  return false;
}

function hasSourcePlacementOverlap(rows: ResultRow[], source: OrchidGroup) {
  if (source.startPosition == null || source.endPosition == null) return false;
  const sourceStart = source.startPosition;
  const sourceEnd = source.endPosition;
  return rows.some((row) => {
    const placement = row.placement;
    return (
      placement != null &&
      placement.bedZoneId === source.bedZoneId &&
      placement.startPosition < sourceEnd &&
      sourceStart < placement.endPosition
    );
  });
}
