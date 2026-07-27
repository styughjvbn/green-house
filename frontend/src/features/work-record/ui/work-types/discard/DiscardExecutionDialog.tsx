"use client";

import { useState } from "react";
import { X } from "lucide-react";
import { transitionWorkOperationTarget } from "../../../api/workRecordApi";
import type { WorkExecutionDialogProps } from "../../../model/workExecution";
import { TextField } from "../../components/FormFields";
import { localDateValue } from "../../components/WorkCompletionDateDialog";

export function DiscardExecutionDialog({
  operation,
  source,
  target,
  onClose,
  onSaved,
}: WorkExecutionDialogProps) {
  const maximumQuantity = Math.min(
    source?.quantity ?? target.remainingQuantity,
    target.remainingQuantity,
  );
  const [discardQuantity, setDiscardQuantity] = useState(
    String(maximumQuantity),
  );
  const [worker, setWorker] = useState(operation.worker ?? "");
  const [reason, setReason] = useState("");
  const today = localDateValue(new Date());
  const [completedDate, setCompletedDate] = useState(today);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (target.id == null) return;
    if (!completedDate) {
      setError("완료일을 입력해주세요.");
      return;
    }
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
          completedDate,
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
        aria-label="폐기 결과 입력"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between border-b p-4">
          <div>
            <p className="text-sm font-semibold text-[#9b341e]">폐기 결과</p>
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
          <TextField
            label="완료일"
            max={today}
            required
            type="date"
            value={completedDate}
            onChange={setCompletedDate}
          />
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
            {saving ? "처리 중" : "폐기 결과 저장"}
          </button>
        </footer>
      </section>
    </div>
  );
}
