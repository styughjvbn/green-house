"use client";

import { useState } from "react";
import { X } from "lucide-react";
import { transitionWorkOperationTarget } from "../../../api/workRecordApi";
import type { WorkExecutionDialogProps } from "../../../model/workExecution";
import { TextField } from "../../components/FormFields";
import { localDateValue } from "../../components/WorkCompletionDateDialog";

export function MovementExecutionDialog({
  bedZones,
  operation,
  source,
  target,
  onClose,
  onSaved,
}: WorkExecutionDialogProps) {
  const initialBedZoneId =
    bedZones.find((zone) => zone.id !== target.locationSnapshot.bedZoneId)
      ?.id ?? bedZones[0]?.id;
  const [bedZoneId, setBedZoneId] = useState(String(initialBedZoneId ?? ""));
  const [startCell, setStartCell] = useState("1");
  const [endCell, setEndCell] = useState("1");
  const [worker, setWorker] = useState(operation.worker ?? "");
  const [memo, setMemo] = useState("");
  const today = localDateValue(new Date());
  const [completedDate, setCompletedDate] = useState(today);
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
      onSaved(
        await transitionWorkOperationTarget(
          operation.id,
          target.id,
          "complete",
          worker.trim() || null,
          {
            toBedZoneId: Number(bedZoneId),
            startPosition: Number(startCell) - 1,
            endPosition: Number(endCell),
            worker: worker.trim() || null,
            memo: memo.trim() || null,
          },
          completedDate,
        ),
      );
      onClose();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "이동 작업을 실행하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  function validate() {
    if (!completedDate) return "완료일을 입력해주세요.";
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
    ) {
      return "현재 위치와 다른 이동 위치를 입력해주세요.";
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
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-3xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label="자리 이동 실행 입력"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between border-b p-4">
          <div>
            <h3 className="font-bold text-[#17251b]">자리 이동 실행 입력</h3>
            <p className="mt-1 text-xs text-[#6a766e]">
              {target.varietyName} · 계획 #{operation.id}
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>

        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm font-semibold text-[#435047]">
              이동할 구역
              <select
                className="mt-1 w-full rounded-md border border-[#cfd8cc] bg-white px-2 py-2 font-normal"
                value={bedZoneId}
                onChange={(event) => setBedZoneId(event.target.value)}
              >
                {bedZones.map((zone) => (
                  <option key={zone.id} value={zone.id}>
                    {zone.houseNumber}동 {zone.physicalBedNumber}다이{" "}
                    {zone.name}
                  </option>
                ))}
              </select>
            </label>
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
            <TextField
              label="완료일"
              max={today}
              required
              type="date"
              value={completedDate}
              onChange={setCompletedDate}
            />
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
            onClick={() => void submit()}
          >
            {saving ? "처리 중" : "이동 완료"}
          </button>
        </footer>
      </section>
    </div>
  );
}
