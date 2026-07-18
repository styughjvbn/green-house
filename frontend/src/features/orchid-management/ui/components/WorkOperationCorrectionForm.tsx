"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useState } from "react";
import type { OrchidGroup } from "@/entities/farm/types";
import { createUuid } from "@/shared/lib/id";
import {
  createWorkOperationCorrection,
  getWorkOperationCorrections,
} from "../../api/orchidManagementApi";
import type { WorkOperationCorrections } from "../../model/types";

export default function WorkOperationCorrectionForm({
  originalWorkOperationId,
  orchidGroup,
  onClose,
}: {
  originalWorkOperationId: number;
  orchidGroup: OrchidGroup;
  onClose: () => void;
}) {
  const router = useRouter();
  const [idempotencyKey, setIdempotencyKey] = useState(createUuid);
  const [title, setTitle] = useState(`${orchidGroup.varietyName} 결과 보정`);
  const [workDate, setWorkDate] = useState(() =>
    new Date().toISOString().slice(0, 10),
  );
  const [quantity, setQuantity] = useState(String(orchidGroup.quantity));
  const [status, setStatus] = useState(orchidGroup.status);
  const [reason, setReason] = useState("");
  const [worker, setWorker] = useState("");
  const [memo, setMemo] = useState("");
  const [corrections, setCorrections] =
    useState<WorkOperationCorrections | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void getWorkOperationCorrections(originalWorkOperationId)
      .then((result) => {
        if (active) setCorrections(result);
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(
            cause instanceof Error
              ? cause.message
              : "보정 이력을 불러오지 못했습니다.",
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [originalWorkOperationId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextQuantity = Number(quantity);
    if (!Number.isInteger(nextQuantity) || nextQuantity < 0) {
      setError("수량은 0 이상의 정수로 입력해주세요.");
      return;
    }
    if (!status.trim() || !reason.trim() || !title.trim()) {
      setError("작업명, 상태, 보정 사유를 입력해주세요.");
      return;
    }
    if (
      nextQuantity === orchidGroup.quantity &&
      status.trim() === orchidGroup.status
    ) {
      setError("수량 또는 상태를 기존 값과 다르게 입력해주세요.");
      return;
    }

    setSaving(true);
    setError(null);
    try {
      const result = await createWorkOperationCorrection(
        originalWorkOperationId,
        {
          idempotencyKey,
          title: title.trim(),
          workDate,
          worker: worker.trim() || null,
          memo: memo.trim() || null,
          reason: reason.trim(),
          orchidGroupAdjustments: [
            {
              orchidGroupId: orchidGroup.id,
              quantity: nextQuantity,
              status: status.trim(),
            },
          ],
        },
      );
      setCorrections(result);
      setIdempotencyKey(createUuid());
      router.refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "보정하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="min-h-0 overflow-y-auto rounded-md border border-[#d5ad63] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-[#8a5a12]">
            구조 변경 결과 보정
          </p>
          <p className="mt-1 text-xs text-[#5c6a60]">
            삭제하지 않고 수량·상태 변경 전후를 작업 이력에 남깁니다.
          </p>
        </div>
        <button
          className="rounded-md border px-2 py-1 text-xs"
          type="button"
          onClick={onClose}
        >
          닫기
        </button>
      </div>

      <div className="mt-3 rounded-md border border-[#ead9b9] bg-[#fffaf0] p-3 text-sm">
        <p className="font-bold text-[#17251b]">{orchidGroup.varietyName}</p>
        <p className="mt-1 text-xs text-[#5c6a60]">
          현재 {orchidGroup.quantity}분 · 상태 {orchidGroup.status} · 원본 작업
          #{originalWorkOperationId}
        </p>
      </div>

      <form className="mt-3 space-y-3" onSubmit={submit}>
        <div className="grid grid-cols-2 gap-2">
          <Field label="보정 작업명" value={title} onChange={setTitle} />
          <Field
            label="작업일"
            type="date"
            value={workDate}
            onChange={setWorkDate}
          />
          <Field
            label="보정 수량"
            type="number"
            min="0"
            value={quantity}
            onChange={setQuantity}
          />
          <Field label="보정 상태" value={status} onChange={setStatus} />
          <Field
            label="작업자"
            required={false}
            value={worker}
            onChange={setWorker}
          />
          <Field
            label="메모"
            required={false}
            value={memo}
            onChange={setMemo}
          />
        </div>
        <label className="block text-xs font-semibold text-[#435047]">
          보정 사유
          <textarea
            className="mt-1 min-h-20 w-full rounded-md border border-[#cbd5c9] bg-white px-2 py-1.5 text-sm"
            maxLength={1000}
            required
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </label>
        {error ? (
          <p className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-2 text-xs text-[#8f2f19]">
            {error}
          </p>
        ) : null}
        <button
          className="w-full rounded-md bg-[#8a5a12] px-3 py-2 text-sm font-bold text-white disabled:opacity-50"
          disabled={saving || loading}
          type="submit"
        >
          {saving ? "보정 저장 중" : "보정 작업 저장"}
        </button>
      </form>

      <div className="mt-4 border-t border-[#e1e6df] pt-3">
        <p className="text-xs font-bold text-[#344138]">기존 보정 이력</p>
        {loading ? (
          <p className="mt-2 text-xs text-[#5c6a60]">확인 중</p>
        ) : corrections?.corrections.length ? (
          <ul className="mt-2 space-y-2">
            {corrections.corrections.map((item) => (
              <li
                key={item.id}
                className="rounded-md border border-[#dfe5dc] bg-[#fbfcfa] p-2 text-xs"
              >
                <p className="font-bold text-[#17251b]">
                  {item.correctionOperation.title}
                </p>
                <p className="mt-1 text-[#5c6a60]">{item.reason}</p>
                {(item.effectDetails.adjustments ?? []).map((adjustment) => (
                  <p
                    className="mt-1 text-[#435047]"
                    key={adjustment.orchidGroupId}
                  >
                    수량 {adjustment.beforeQuantity} →{" "}
                    {adjustment.afterQuantity} · 상태 {adjustment.beforeStatus}{" "}
                    → {adjustment.afterStatus}
                  </p>
                ))}
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-2 text-xs text-[#5c6a60]">등록된 보정이 없습니다.</p>
        )}
      </div>
    </section>
  );
}

function Field({
  label,
  value,
  onChange,
  required = true,
  ...inputProps
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange">) {
  return (
    <label className="block text-xs font-semibold text-[#435047]">
      {label}
      <input
        {...inputProps}
        className="mt-1 w-full rounded-md border border-[#cbd5c9] bg-white px-2 py-1.5 text-sm"
        required={required}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
