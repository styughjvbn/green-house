"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { Banknote, ChevronDown, ChevronUp } from "lucide-react";
import type { PartnerPaymentEvent } from "@/entities/farm/types";
import {
  getPaymentEvents,
  type ManualPaymentPayload,
} from "../../api/salesApi";

export function ManualPaymentPanel({
  targetType,
  targetId,
  remainingAmount,
  expectedPaymentDate,
  onConfirm,
}: {
  targetType: "SALES_SLIP" | "AUCTION_SETTLEMENT";
  targetId: number;
  remainingAmount: number;
  expectedPaymentDate: string | null;
  onConfirm: (payload: ManualPaymentPayload) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState(String(remainingAmount));
  const [paymentDate, setPaymentDate] = useState(
    new Date().toISOString().slice(0, 10),
  );
  const [paymentMethod, setPaymentMethod] = useState("계좌이체");
  const [depositorName, setDepositorName] = useState("");
  const [worker, setWorker] = useState("관리자");
  const [memo, setMemo] = useState("");
  const [events, setEvents] = useState<PartnerPaymentEvent[]>([]);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function toggle() {
    const next = !open;
    setOpen(next);
    if (!next) return;
    setAmount(String(remainingAmount));
    try {
      setEvents(await getPaymentEvents(targetType, targetId));
    } catch {
      setEvents([]);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      await onConfirm({
        amount: Number(amount),
        paymentDate,
        paymentMethod: paymentMethod.trim() || null,
        depositorName: depositorName.trim() || null,
        worker: worker.trim() || null,
        memo: memo.trim() || null,
      });
      setEvents(await getPaymentEvents(targetType, targetId));
      setMessage("입금 확인 완료");
      setAmount(String(Math.max(0, remainingAmount - Number(amount))));
      setMemo("");
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "입금을 확인하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  const receivedEvents = events.filter(
    (event) => event.eventType === "PAYMENT_RECEIVED",
  );

  return (
    <div className="border-t border-[#e5e9e3]">
      <button
        className="flex w-full items-center justify-between px-4 py-3 text-sm font-bold"
        type="button"
        onClick={toggle}
      >
        <span className="inline-flex items-center gap-2">
          <Banknote className="h-4 w-4 text-[#159447]" />
          입금 확인
        </span>
        {open ? (
          <ChevronUp className="h-4 w-4" />
        ) : (
          <ChevronDown className="h-4 w-4" />
        )}
      </button>

      {open ? (
        <div className="space-y-3 border-t border-[#edf0ec] px-4 py-3">
          <div className="flex flex-wrap gap-x-5 gap-y-1 text-xs text-[#68756c]">
            <span>현재 잔액 {remainingAmount.toLocaleString()}원</span>
            <span>입금 예정일 {expectedPaymentDate ?? "-"}</span>
          </div>

          {remainingAmount > 0 ? (
            <form className="grid gap-2 sm:grid-cols-2" onSubmit={submit}>
              <Field label="입금액">
                <input
                  className={controlClass}
                  type="number"
                  min={1}
                  max={remainingAmount}
                  required
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                />
              </Field>
              <Field label="입금일">
                <input
                  className={controlClass}
                  type="date"
                  required
                  value={paymentDate}
                  onChange={(event) => setPaymentDate(event.target.value)}
                />
              </Field>
              <Field label="입금 방식">
                <select
                  className={controlClass}
                  value={paymentMethod}
                  onChange={(event) => setPaymentMethod(event.target.value)}
                >
                  <option>계좌이체</option>
                  <option>현금</option>
                  <option>카드</option>
                  <option>기타</option>
                </select>
              </Field>
              <Field label="입금자명">
                <input
                  className={controlClass}
                  value={depositorName}
                  onChange={(event) => setDepositorName(event.target.value)}
                />
              </Field>
              <Field label="확인자">
                <input
                  className={controlClass}
                  value={worker}
                  onChange={(event) => setWorker(event.target.value)}
                />
              </Field>
              <Field label="메모">
                <input
                  className={controlClass}
                  value={memo}
                  onChange={(event) => setMemo(event.target.value)}
                />
              </Field>
              <button
                className="h-9 rounded-md bg-[#159447] px-4 text-xs font-semibold text-white disabled:opacity-50 sm:col-span-2 sm:justify-self-end"
                type="submit"
                disabled={saving}
              >
                {saving ? "처리 중" : "입금 확정"}
              </button>
            </form>
          ) : (
            <p className="text-xs font-semibold text-[#158442]">입금 완료</p>
          )}

          {message ? <p className="text-xs font-semibold">{message}</p> : null}

          {receivedEvents.length > 0 ? (
            <div>
              <p className="mb-1.5 text-xs font-bold">입금 이력</p>
              <div className="divide-y divide-[#edf0ec] rounded-md border border-[#e1e6df] text-xs">
                {receivedEvents.map((event) => (
                  <div
                    key={event.id}
                    className="flex flex-wrap justify-between gap-2 px-3 py-2"
                  >
                    <span>
                      {event.eventDate} ·{" "}
                      {event.depositorName || "입금자 미기재"}
                    </span>
                    <strong>{event.amount.toLocaleString()}원</strong>
                  </div>
                ))}
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

const controlClass =
  "mt-1 h-9 w-full rounded-md border border-[#ccd5ca] bg-white px-3 text-sm";

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="text-xs font-semibold text-[#526057]">
      {label}
      {children}
    </label>
  );
}
