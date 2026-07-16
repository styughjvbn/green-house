"use client";

import { useState } from "react";
import { Plus, Trash2, X } from "lucide-react";
import type {
  BedZone,
  OrchidGroup,
  WorkOperation,
  WorkOperationTarget,
} from "@/entities/farm/types";
import { POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";
import { createUuid } from "@/shared/lib/id";
import { transitionWorkOperationTarget } from "../../api/workRecordApi";
import { TextField } from "./FormFields";

type ResultRow = {
  key: string;
  bedZoneId: string;
  quantity: string;
  potSize: string;
  ageYear: string;
  startCell: string;
  endCell: string;
};

export function StructureWorkExecutionDialog({
  bedZones,
  operation,
  source,
  target,
  onClose,
  onSaved,
}: {
  bedZones: BedZone[];
  operation: WorkOperation;
  source: OrchidGroup | null;
  target: WorkOperationTarget;
  onClose: () => void;
  onSaved: (operation: WorkOperation) => void;
}) {
  const isRepot = operation.workTypeCode === "REPOT";
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
  const [actualQuantity, setActualQuantity] = useState(
    String(Math.max(1, target.quantitySnapshot)),
  );
  const [potSize, setPotSize] = useState(
    source?.potSize ?? target.potSizeSnapshot ?? "",
  );
  const [ageYear, setAgeYear] = useState(
    source?.ageYear == null ? "" : String(source.ageYear),
  );
  const [bedZoneId, setBedZoneId] = useState(String(initialBedZoneId ?? ""));
  const [startCell, setStartCell] = useState("1");
  const [endCell, setEndCell] = useState("1");
  const [rows, setRows] = useState<ResultRow[]>(() => [
    newResultRow(source, bedZones[0]?.id),
  ]);
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
        : isRepot
          ? {
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
                bedZoneId: Number(row.bedZoneId),
                quantity: Number(row.quantity),
                potSize: row.potSize || null,
                ageYear: row.ageYear ? Number(row.ageYear) : null,
                placementType: null,
                trayCount: null,
                splitPlacementAllowed: false,
                startPosition: Number(row.startCell) - 1,
                endPosition: Number(row.endCell),
                memo: null,
              })),
            }
          : {
              pottingDate: new Date().toISOString().slice(0, 10),
              actualQuantity: Number(actualQuantity),
              potSize: potSize || null,
              ageYear: ageYear ? Number(ageYear) : null,
              growthStage: null,
              placementType: null,
              trayCount: null,
              bedZoneId: Number(bedZoneId),
              startPosition: Number(startCell) - 1,
              endPosition: Number(endCell),
              worker: worker.trim() || null,
              memo: memo.trim() || null,
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
            !row.bedZoneId ||
            Number(row.quantity) < 1 ||
            Number(row.startCell) < 1 ||
            Number(row.endCell) < Number(row.startCell),
        )
      )
        return "결과 난 묶음의 위치와 수량을 확인해주세요.";
      return null;
    }
    if (!bedZoneId || Number(actualQuantity) < 1)
      return "배치 위치와 실제 수량을 확인해주세요.";
    if (Number(startCell) < 1 || Number(endCell) < Number(startCell))
      return "배치 시작·끝 칸을 확인해주세요.";
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
                      setRows((current) => [
                        ...current,
                        newResultRow(source, bedZones[0]?.id),
                      ])
                    }
                  >
                    <Plus className="h-3 w-3" aria-hidden="true" /> 추가
                  </button>
                </div>
                {rows.map((row, index) => (
                  <ResultRowFields
                    key={row.key}
                    bedZones={bedZones}
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
          ) : isMovement ? (
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
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              <TextField
                label="실제 수량"
                type="number"
                value={actualQuantity}
                onChange={setActualQuantity}
              />
              <SelectInput
                label="배치 구역"
                value={bedZoneId}
                onChange={setBedZoneId}
                options={bedZones.map((zone) => ({
                  value: String(zone.id),
                  label: `${zone.houseNumber}동 ${zone.physicalBedNumber}다이 ${zone.name}`,
                }))}
              />
              <PotSizeInput value={potSize} onChange={setPotSize} />
              <TextField
                label="년생"
                type="number"
                value={ageYear}
                onChange={setAgeYear}
              />
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

function ResultRowFields({
  bedZones,
  index,
  row,
  removable,
  onChange,
  onRemove,
}: {
  bedZones: BedZone[];
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
        <SelectInput
          label="배치 구역"
          value={row.bedZoneId}
          onChange={(value) => onChange({ bedZoneId: value })}
          options={bedZones.map((zone) => ({
            value: String(zone.id),
            label: `${zone.houseNumber}동 ${zone.physicalBedNumber}다이 ${zone.name}`,
          }))}
        />
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
        <TextField
          label="시작 칸"
          type="number"
          value={row.startCell}
          onChange={(value) => onChange({ startCell: value })}
        />
        <TextField
          label="끝 칸"
          type="number"
          value={row.endCell}
          onChange={(value) => onChange({ endCell: value })}
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

function newResultRow(source: OrchidGroup | null, zoneId?: number): ResultRow {
  return {
    key: createUuid(),
    bedZoneId: String(zoneId ?? source?.bedZoneId ?? ""),
    quantity: String(source?.quantity ?? 1),
    potSize: source?.potSize ?? "",
    ageYear: source?.ageYear == null ? "" : String(source.ageYear),
    startCell: "1",
    endCell: "1",
  };
}
