"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import type { AuctionLot } from "@/entities/farm/types";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import type {
  AuctionArrival,
  AuctionFollowUp,
  AuctionFollowUpMethod,
} from "../../api/types";
import type { AuctionFollowUpInput } from "../../lib/auctionFollowUpRequest";
import { SalesReturnArrivalForm } from "../SalesReturnArrivalForm";
import { followUpLabels } from "../../lib/auctionDisplay";

const inputClass =
  "mt-1 h-10 w-full rounded-md border border-[#d7ddd8] bg-white px-3 text-sm";
export function AuctionFollowUpDialog({
  mode,
  lot,
  summary,
  saving,
  blocked,
  onClose,
  onReturnFocus,
  onSubmit,
}: {
  mode: "decision" | "arrival" | AuctionArrival;
  lot: AuctionLot;
  summary: AuctionFollowUp;
  saving: boolean;
  blocked: boolean;
  onClose: () => void;
  onReturnFocus: () => void;
  onSubmit: (input: AuctionFollowUpInput) => Promise<void>;
}) {
  const { businessDate } = useRuntimeContext();
  const [method, setMethod] = useState<AuctionFollowUpMethod | "">("");
  const [date, setDate] = useState(businessDate);
  const [reason, setReason] = useState("");
  const [worker, setWorker] = useState("");
  const [error, setError] = useState<string | null>(null);
  const cancellation = typeof mode === "object" ? mode : null;
  const title =
    mode === "decision"
      ? "유찰 잔량 처리 방법 선택"
      : mode === "arrival"
        ? "실제 반환품 도착 기록"
        : "도착 취소 정정";
  const disabled = blocked;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    try {
      let input: AuctionFollowUpInput;
      if (mode === "decision") {
        if (!method || !summary.availableMethods.includes(method))
          throw new Error("처리 방법을 선택하세요.");
        input = {
          operation: "FOLLOW_UP",
          payload: {
            method,
            reason: reason.trim(),
            ...(worker.trim() ? { worker: worker.trim() } : {}),
          },
        };
      } else if (typeof mode === "object") {
        input = {
          operation: "ARRIVAL_CANCEL",
          arrivalId: mode.id,
          payload: {
            correctionDate: date,
            reason: reason.trim(),
            ...(worker.trim() ? { worker: worker.trim() } : {}),
          },
        };
      } else return;
      await onSubmit(input);
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : "저장하지 못했습니다.",
      );
    }
  }
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !saving) onClose();
      }}
    >
      <DialogContent
        className="max-h-[90dvh] max-w-2xl overflow-y-auto rounded-lg p-5"
        showCloseButton={false}
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          onReturnFocus();
        }}
      >
        <DialogTitle className="pr-10 text-lg font-bold">{title}</DialogTitle>
        <DialogDescription className="mt-2 text-sm text-[#68756c]">
          LOT #{lot.id} · {lot.varietyName}
        </DialogDescription>
        {mode === "arrival" ? (
          <SalesReturnArrivalForm
            pendingQuantity={summary.pendingQuantity}
            saving={saving}
            blocked={blocked}
            onClose={onClose}
            onSubmit={(payload) => onSubmit({ operation: "ARRIVAL", payload })}
          />
        ) : (
          <form className="mt-4 space-y-4" onSubmit={submit}>
            <fieldset
              disabled={disabled}
              className="space-y-4 disabled:opacity-60"
            >
              {mode === "decision" ? (
                <>
                  <p className="text-sm">
                    유찰 잔량 전체에 한 가지 방법을 선택합니다.
                  </p>
                  <div className="space-y-2">
                    {summary.availableMethods.map((choice) => (
                      <label
                        key={choice}
                        className="flex cursor-pointer gap-3 rounded border bg-white p-3 text-sm font-semibold"
                      >
                        <input
                          type="radio"
                          name="followUpMethod"
                          required
                          value={choice}
                          checked={method === choice}
                          onChange={() => setMethod(choice)}
                        />
                        {followUpLabels[choice]}
                      </label>
                    ))}
                  </div>
                  {method === "FARM_RETURN" ? (
                    <p className="text-sm">
                      이 결정은 농장 재고를 만들지 않습니다. 도착한 뒤 수량과
                      배치를 기록하세요.
                    </p>
                  ) : null}
                  {method === "AUCTION_DISPOSAL" ? (
                    <p className="text-sm">
                      저장하면 잔량 전체의 경매장 처리가 완료됩니다. 농장 재고는
                      바뀌지 않습니다.
                    </p>
                  ) : null}
                </>
              ) : null}
              {cancellation ? (
                <>
                  <p className="text-sm">
                    도착 기록 #{cancellation.id} ·{" "}
                    {cancellation.quantity.toLocaleString()}분을 취소합니다.
                    {cancellation.recordOnly
                      ? "도착 기록만 취소하며 농장 재고는 변경하지 않습니다."
                      : `생성 난 묶음 #${cancellation.orchidGroupId}의 재고 효과도 취소합니다. 후속 사용을 먼저 정정해야 합니다.`}
                  </p>
                  <Field label="정정일">
                    <input
                      className={inputClass}
                      type="date"
                      required
                      value={date}
                      onChange={(e) => setDate(e.target.value)}
                    />
                  </Field>
                </>
              ) : null}
              {
                <Field
                  label={mode === "decision" ? "결정 사유" : "도착 취소 사유"}
                >
                  <textarea
                    className={`${inputClass} h-20 py-2`}
                    required
                    maxLength={1000}
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                  />
                </Field>
              }
              <Field label="담당자 (선택)">
                <input
                  className={inputClass}
                  maxLength={100}
                  value={worker}
                  onChange={(e) => setWorker(e.target.value)}
                />
              </Field>
            </fieldset>
            {error ? (
              <p
                role="alert"
                className="text-sm whitespace-pre-line text-red-700"
              >
                {error}
              </p>
            ) : null}
            <div className="flex justify-end gap-2">
              <button
                type="button"
                disabled={saving}
                className="rounded border px-4 py-2 disabled:opacity-50"
                onClick={onClose}
              >
                닫기
              </button>
              <button
                type="submit"
                disabled={disabled}
                className="rounded bg-[#159447] px-4 py-2 font-bold text-white disabled:opacity-50"
              >
                {saving
                  ? "저장 중…"
                  : mode === "decision"
                    ? "처리 방법 저장"
                    : "도착 취소 저장"}
              </button>
            </div>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block text-xs font-semibold text-[#425047]">
      {label}
      {children}
    </label>
  );
}
