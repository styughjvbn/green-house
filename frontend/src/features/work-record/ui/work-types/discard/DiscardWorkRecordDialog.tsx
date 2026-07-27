"use client";

import { useState } from "react";
import { X } from "lucide-react";
import type { OrchidGroup } from "@/entities/farm/types";
import { TextField } from "../../components/FormFields";

type DiscardResult = {
  orchidGroupId: number;
  discardQuantity: number;
  reason: string | null;
};

export function DiscardWorkRecordDialog({
  groups,
  initialCompletedDate,
  initialWorker,
  onClose,
  onSubmit,
}: {
  groups: OrchidGroup[];
  initialCompletedDate: string;
  initialWorker: string;
  onClose: () => void;
  onSubmit: (values: {
    completedDate: string;
    worker: string | null;
    results: DiscardResult[];
  }) => Promise<void>;
}) {
  const [completedDate, setCompletedDate] = useState(initialCompletedDate);
  const [worker, setWorker] = useState(initialWorker);
  const [quantities, setQuantities] = useState<Record<number, string>>(() =>
    Object.fromEntries(
      groups.map((group) => [group.id, String(group.quantity)]),
    ),
  );
  const [reasons, setReasons] = useState<Record<number, string>>(() =>
    Object.fromEntries(groups.map((group) => [group.id, ""])),
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!completedDate) {
      setError("작업일을 입력해주세요.");
      return;
    }
    const invalid = groups.find((group) => {
      const quantity = Number(quantities[group.id]);
      return (
        !Number.isInteger(quantity) || quantity < 1 || quantity > group.quantity
      );
    });
    if (invalid) {
      setError(
        `${invalid.varietyName}의 폐기 수량은 1 이상 ${invalid.quantity} 이하로 입력해주세요.`,
      );
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await onSubmit({
        completedDate,
        worker: worker.trim() || null,
        results: groups.map((group) => ({
          orchidGroupId: group.id,
          discardQuantity: Number(quantities[group.id]),
          reason: reasons[group.id]?.trim() || null,
        })),
      });
      onClose();
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "폐기 작업 기록을 저장하지 못했습니다.",
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
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-3xl flex-col overflow-hidden rounded-lg bg-white shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label="폐기 작업 결과 입력"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between border-b p-4">
          <div>
            <h3 className="font-bold text-[#17251b]">폐기 작업 결과 입력</h3>
            <p className="mt-1 text-xs text-[#6a766e]">
              선택한 모든 난 묶음의 폐기 수량과 사유를 입력하세요.
            </p>
          </div>
          <button type="button" aria-label="닫기" onClick={onClose}>
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>

        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField
              label="작업일"
              required
              type="date"
              value={completedDate}
              onChange={setCompletedDate}
            />
            <TextField label="작업자" value={worker} onChange={setWorker} />
          </div>
          <div className="space-y-2">
            {groups.map((group) => (
              <section
                className="grid gap-3 rounded-md border bg-[#f8faf7] p-3 sm:grid-cols-[minmax(0,1fr)_140px_minmax(0,1fr)]"
                key={group.id}
              >
                <div className="text-sm">
                  <p className="font-bold text-[#26352b]">
                    {group.varietyName}
                  </p>
                  <p className="mt-1 text-xs text-[#6a766e]">
                    {group.houseNumber}동 {group.physicalBedNumber}다이{" "}
                    {group.bedZoneName} · 현재 {group.quantity}분
                  </p>
                </div>
                <TextField
                  label="폐기 수량"
                  required
                  type="number"
                  value={quantities[group.id] ?? ""}
                  onChange={(value) =>
                    setQuantities((current) => ({
                      ...current,
                      [group.id]: value,
                    }))
                  }
                />
                <TextField
                  label="폐기 사유"
                  value={reasons[group.id] ?? ""}
                  onChange={(value) =>
                    setReasons((current) => ({
                      ...current,
                      [group.id]: value,
                    }))
                  }
                />
              </section>
            ))}
          </div>
          <p className="rounded-md bg-[#fff7ed] p-3 text-sm text-[#8a4b16]">
            일부 폐기는 잔여 수량을 유지하고, 전량 폐기는 난 묶음 상태를 폐기로
            변경합니다.
          </p>
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
            className="rounded-md bg-[#b5472f] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
            disabled={saving || groups.length === 0}
            type="button"
            onClick={() => void submit()}
          >
            {saving ? "처리 중" : "폐기 작업 기록 저장"}
          </button>
        </footer>
      </section>
    </div>
  );
}
