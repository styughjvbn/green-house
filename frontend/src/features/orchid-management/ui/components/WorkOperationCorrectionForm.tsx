"use client";

import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";
import type { OrchidGroup } from "@/entities/farm/types";
import { createUuid } from "@/shared/lib/id";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import {
  createWorkOperationCorrection,
  getWorkOperationCorrections,
} from "../../api/orchidManagementApi";

export default function WorkOperationCorrectionForm({
  originalWorkOperationId,
  orchidGroup,
  onClose,
  onPendingChange,
  onSaved,
  showHistory = true,
}: {
  originalWorkOperationId: number;
  orchidGroup: OrchidGroup;
  onClose: () => void;
  onPendingChange?: (pending: boolean) => void;
  onSaved?: () => void;
  showHistory?: boolean;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { businessDate } = useRuntimeContext();
  const [idempotencyKey, setIdempotencyKey] = useState(createUuid);
  const [workDateDraft, setWorkDate] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(String(orchidGroup.quantity));
  const [status, setStatus] = useState(orchidGroup.status);
  const [reason, setReason] = useState("");
  const [worker, setWorker] = useState("");
  const [memo, setMemo] = useState("");
  const [cancelResultCreation, setCancelResultCreation] = useState(false);
  const correctionQuery = useQuery({
    queryKey: [
      "workRecords",
      "operations",
      originalWorkOperationId,
      "corrections",
    ],
    queryFn: () => getWorkOperationCorrections(originalWorkOperationId),
  });
  const corrections = correctionQuery.data;
  const quantityCorrectionEnabled =
    corrections?.quantityCorrectionEnabled === true;
  const balance = corrections?.quantityBalances.find(
    (item) => orchidGroup.id in (item.resultQuantities ?? {}),
  );
  const [correctInputs, setCorrectInputs] = useState(false);
  const [inputDrafts, setInputDrafts] = useState<Record<string, string>>({});
  const [lossDraft, setLossDraft] = useState<string | null>(null);
  const [growthDraft, setGrowthDraft] = useState<string | null>(null);
  const quantityChanged =
    quantityCorrectionEnabled && Number(quantity) !== orchidGroup.quantity;
  const lossQuantity = lossDraft ?? String(balance?.lossQuantity ?? 0);
  const increaseQuantity =
    growthDraft ?? String(balance?.increaseQuantity ?? 0);
  const workDate =
    workDateDraft ??
    corrections?.originalOperation.plannedStartDate ??
    businessDate;
  const loading = correctionQuery.isPending;
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextQuantity = quantityCorrectionEnabled
      ? Number(quantity)
      : orchidGroup.quantity;
    if (!Number.isInteger(nextQuantity) || nextQuantity < 0) {
      setError("수량은 0 이상의 정수로 입력해주세요.");
      return;
    }
    if ((!cancelResultCreation && !status.trim()) || !reason.trim()) {
      setError("상태와 보정 사유를 입력해주세요.");
      return;
    }
    if (
      !cancelResultCreation &&
      nextQuantity === orchidGroup.quantity &&
      (!quantityCorrectionEnabled ||
        (!correctInputs && lossDraft == null && growthDraft == null)) &&
      status.trim() === orchidGroup.status &&
      workDate === corrections?.originalOperation.plannedStartDate
    ) {
      setError(
        quantityCorrectionEnabled
          ? "수량, 상태 또는 작업일을 기존 값과 다르게 입력해주세요."
          : "상태 또는 작업일을 기존 값과 다르게 입력해주세요.",
      );
      return;
    }

    setSaving(true);
    onPendingChange?.(true);
    setError(null);
    try {
      await createWorkOperationCorrection(originalWorkOperationId, {
        idempotencyKey,
        workDate,
        worker: worker.trim() || undefined,
        memo: memo.trim() || undefined,
        reason: reason.trim(),
        cancelResultCreation,
        orchidGroupAdjustments:
          cancelResultCreation ||
          quantityChanged ||
          status.trim() !== orchidGroup.status
            ? [
                {
                  orchidGroupId: orchidGroup.id,
                  quantity: nextQuantity,
                  status: status.trim(),
                },
              ]
            : [],
        quantityCorrections:
          quantityCorrectionEnabled &&
          !cancelResultCreation &&
          balance &&
          (quantityChanged ||
            correctInputs ||
            lossDraft != null ||
            growthDraft != null)
            ? [
                {
                  executionId: balance.executionId!,
                  sourceInputQuantities: correctInputs
                    ? Object.fromEntries(
                        Object.entries(balance.sourceInputQuantities ?? {}).map(
                          ([id, amount]) => [
                            id,
                            Number(inputDrafts[id] ?? amount),
                          ],
                        ),
                      )
                    : undefined,
                  lossQuantity: Number(lossQuantity),
                  increaseQuantity: Number(increaseQuantity),
                },
              ]
            : [],
      });
      setIdempotencyKey(createUuid());
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["workRecords", "operations"],
        }),
        queryClient.invalidateQueries({ queryKey: ["farm-status"] }),
        queryClient.invalidateQueries({ queryKey: ["orchid-groups"] }),
      ]);
      router.refresh();
      if (onSaved) onSaved();
      else if (cancelResultCreation) onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "보정하지 못했습니다.");
    } finally {
      setSaving(false);
      onPendingChange?.(false);
    }
  }

  return (
    <section className="min-h-0 overflow-y-auto rounded-md border border-[#d5ad63] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-bold text-[#8a5a12]">작업 기록 정정</p>
          <p className="mt-1 text-xs text-[#5c6a60]">
            {quantityCorrectionEnabled
              ? "작업 당시 입력 오류만 정정합니다."
              : "수량 정정은 현재 비활성화되어 있습니다. 작업일·상태 정정과 결과 생성 취소는 가능합니다."}
          </p>
        </div>
        <button
          className="rounded-md border px-2 py-1 text-xs"
          type="button"
          disabled={saving}
          onClick={onClose}
        >
          닫기
        </button>
      </div>

      <div className="mt-3 rounded-md border border-[#ead9b9] bg-[#fffaf0] p-3 text-sm">
        <p className="font-bold text-[#17251b]">{orchidGroup.varietyName}</p>
        <p className="mt-1 text-xs text-[#5c6a60]">
          현재 {orchidGroup.quantity}분 · 상태 {orchidGroup.status} · 작업일{" "}
          {corrections?.originalOperation.plannedStartDate ?? "확인 중"} · 원본
          작업 #{originalWorkOperationId}
        </p>
      </div>

      <form className="mt-3" onSubmit={submit}>
        <fieldset className="space-y-3" disabled={saving || loading}>
          <label className="flex items-start gap-2 rounded-md border border-[#d9b8ae] bg-[#fff6f2] p-3 text-xs text-[#713321]">
            <input
              checked={cancelResultCreation}
              className="mt-0.5"
              type="checkbox"
              onChange={(event) =>
                setCancelResultCreation(event.target.checked)
              }
            />
            <span>
              <strong className="block">오생성 보정</strong>
              <span className="mt-1 block">
                잘못 생성된 난 묶음을 수량 0분·생성 취소 상태로 보정합니다. 폐기
                이력으로 기록되지 않습니다.
              </span>
            </span>
          </label>
          <div className="grid grid-cols-2 gap-2">
            <Field
              label="보정 후 작업일"
              type="date"
              value={workDate}
              onChange={setWorkDate}
              disabled={cancelResultCreation}
            />
            {quantityCorrectionEnabled ? (
              <Field
                label="보정 수량"
                type="number"
                min="0"
                value={quantity}
                onChange={setQuantity}
                disabled={cancelResultCreation}
              />
            ) : null}
            <Field
              label="보정 상태"
              value={status}
              onChange={setStatus}
              disabled={cancelResultCreation}
            />
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
          {quantityCorrectionEnabled && !cancelResultCreation && balance ? (
            <div className="space-y-2 rounded border border-[#ead9b9] p-3 text-xs">
              <p>
                현재 유효 작업 기록: 투입 {balance.inputQuantity} · 결과{" "}
                {balance.resultQuantity} · 손실 {balance.lossQuantity} · 증식{" "}
                {balance.increaseQuantity}분
              </p>
              <p>
                투입 + 증식 = 결과 + 손실이어야 합니다. 원본 수량은 자동
                변경하지 않습니다.
              </p>
              {balance.inputEditable ? (
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={correctInputs}
                    onChange={(e) => setCorrectInputs(e.target.checked)}
                  />
                  원본별 투입량 입력 오류도 정정
                </label>
              ) : null}
              {correctInputs
                ? Object.entries(balance.sourceInputQuantities ?? {}).map(
                    ([id, amount]) => (
                      <Field
                        key={id}
                        label={`원본 #${id} 정정 투입량`}
                        type="number"
                        min="1"
                        value={inputDrafts[id] ?? String(amount)}
                        onChange={(value) =>
                          setInputDrafts((previous) => ({
                            ...previous,
                            [id]: value,
                          }))
                        }
                      />
                    ),
                  )
                : null}
              <div className="grid grid-cols-2 gap-2">
                <Field
                  label="정정 손실 수량"
                  type="number"
                  min="0"
                  value={lossQuantity}
                  onChange={setLossDraft}
                  disabled={!balance.lossEditable}
                />
                <Field
                  label="정정 증식 수량"
                  type="number"
                  min="0"
                  value={increaseQuantity}
                  onChange={setGrowthDraft}
                  disabled={!balance.increaseAllowed}
                />
              </div>
              {!balance.lossEditable ? (
                <p>
                  연관 폐기량 변경은 이동·폐기를 취소 후 함께 다시 기록해야
                  합니다.
                </p>
              ) : null}
              <p>
                최초 작업 기록은 보존하고 정정된 수량 수지를 감사 내역으로
                남깁니다.
              </p>
            </div>
          ) : null}
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
          {error || correctionQuery.error ? (
            <p className="rounded-md border border-[#c25a3c] bg-[#fff1ec] p-2 text-xs text-[#8f2f19]">
              {error ??
                (correctionQuery.error instanceof Error
                  ? correctionQuery.error.message
                  : "보정 내역을 불러오지 못했습니다.")}
            </p>
          ) : null}
          <button
            className="w-full rounded-md bg-[#8a5a12] px-3 py-2 text-sm font-bold text-white disabled:opacity-50"
            disabled={saving || loading}
            type="submit"
          >
            {saving
              ? "저장 중"
              : cancelResultCreation
                ? "오생성 보정"
                : "보정 내역 저장"}
          </button>
        </fieldset>
      </form>

      {showHistory ? (
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
                    보정 · {item.createdAt}
                    {item.worker ? ` · ${item.worker}` : ""}
                  </p>
                  <p className="mt-1 text-[#5c6a60]">{item.reason}</p>
                  {item.beforeWorkDate &&
                  item.afterWorkDate &&
                  item.beforeWorkDate !== item.afterWorkDate ? (
                    <p className="mt-1 text-[#435047]">
                      작업일 {item.beforeWorkDate} → {item.afterWorkDate}
                    </p>
                  ) : null}
                  {(item.adjustments ?? []).map((adjustment) => (
                    <p
                      className="mt-1 text-[#435047]"
                      key={adjustment.orchidGroupId}
                    >
                      수량 {adjustment.beforeQuantity} →{" "}
                      {adjustment.afterQuantity} · 상태{" "}
                      {adjustment.beforeStatus} → {adjustment.afterStatus}
                    </p>
                  ))}
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-2 text-xs text-[#5c6a60]">
              등록된 보정이 없습니다.
            </p>
          )}
        </div>
      ) : null}
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
