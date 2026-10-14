"use client";

import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { Banknote, ChevronDown, ChevronUp } from "lucide-react";
import type { PaymentTargetType } from "@/entities/farm/types";
import { createUuid } from "@/shared/lib/id";
import { PaginationControls } from "@/shared/ui/PaginationControls";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { readPaymentHistoryPage } from "../../lib/salesRouteParams";
import { salesQueryKeys } from "../../model/salesQueryKeys";
import { targetPaymentHistoryPageQueryOptions } from "../../model/salesQueryOptions";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import type { ManualPaymentPayload } from "../../api/salesApi";

export function ManualPaymentPanel({
  targetType,
  targetId,
  remainingAmount,
  expectedPaymentDate,
  paymentAllowed = true,
  onConfirm,
}: {
  targetType: Exclude<PaymentTargetType, "NONE">;
  targetId: number;
  remainingAmount: number | null;
  expectedPaymentDate: string | null;
  paymentAllowed?: boolean;
  onConfirm: (payload: ManualPaymentPayload) => Promise<number | null>;
}) {
  const { businessDate } = useRuntimeContext();
  const queryClient = useQueryClient();
  const writeUrlParams = useUrlSearchParamsWriter();
  const historyPage = readPaymentHistoryPage(useSearchParams());
  const open = historyPage != null;
  const eventsQuery = useQuery({
    ...targetPaymentHistoryPageQueryOptions(
      targetType,
      targetId,
      historyPage ?? 0,
    ),
    enabled: open,
  });
  const events = eventsQuery.data;
  const [amount, setAmount] = useState(String(remainingAmount ?? ""));
  const [paymentDate, setPaymentDate] = useState(businessDate);
  const [paymentMethod, setPaymentMethod] = useState("계좌이체");
  const [depositorName, setDepositorName] = useState("");
  const [worker, setWorker] = useState("관리자");
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState(createUuid);

  useEffect(() => {
    if (
      historyPage != null &&
      events &&
      historyPage >= Math.max(1, events.totalPages)
    ) {
      writeUrlParams((params) =>
        params.set("paymentPage", String(Math.max(0, events.totalPages - 1))),
      );
    }
  }, [historyPage, events, writeUrlParams]);

  function toggle() {
    if (!open) setAmount(String(remainingAmount ?? ""));
    writeUrlParams((params) => {
      if (open) params.delete("paymentPage");
      else params.set("paymentPage", "0");
    }, "push");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setMessage(null);
    try {
      const remaining = await onConfirm({
        amount: Number(amount),
        paymentDate,
        idempotencyKey,
        paymentMethod: paymentMethod.trim() || null,
        depositorName: depositorName.trim() || null,
        worker: worker.trim() || null,
        memo: memo.trim() || null,
      });
      setMessage("수납 기록 완료");
      setAmount(String(remaining ?? ""));
      setMemo("");
      setIdempotencyKey(createUuid());
      const queryKey = salesQueryKeys.payments.target(targetType, targetId);
      await queryClient.cancelQueries({ queryKey });
      void queryClient.invalidateQueries({ queryKey });
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "수납을 기록하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="border-t border-[#e5e9e3]">
      <button
        className="flex w-full items-center justify-between px-4 py-3 text-sm font-bold"
        type="button"
        onClick={toggle}
        aria-expanded={open}
      >
        <span className="inline-flex items-center gap-2">
          <Banknote className="h-4 w-4 text-[#159447]" />
          {paymentAllowed ? "수납 기록" : "수납 이력"}
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
            <span>
              현재 잔액{" "}
              {remainingAmount == null
                ? "미확인"
                : `${remainingAmount.toLocaleString()}원`}
            </span>
            <span>수납 예정일 {formatShortDate(expectedPaymentDate)}</span>
          </div>

          {paymentAllowed && remainingAmount != null && remainingAmount > 0 ? (
            <form onSubmit={submit}>
              <fieldset disabled={saving} className="grid gap-2 sm:grid-cols-2">
                <Field label="수납액">
                  <input
                    className={controlClass}
                    type="number"
                    min={1}
                    max={Number.MAX_SAFE_INTEGER}
                    step={1}
                    required
                    value={amount}
                    onChange={(event) => {
                      setAmount(event.target.value);
                      setIdempotencyKey(createUuid());
                    }}
                  />
                </Field>
                <Field label="수납일">
                  <input
                    className={controlClass}
                    type="date"
                    required
                    value={paymentDate}
                    onChange={(event) => {
                      setPaymentDate(event.target.value);
                      setIdempotencyKey(createUuid());
                    }}
                  />
                </Field>
                <Field label="수납 방법">
                  <select
                    className={controlClass}
                    value={paymentMethod}
                    onChange={(event) => setPaymentMethod(event.target.value)}
                  >
                    <option>계좌이체</option>
                    <option>현금</option>
                  </select>
                </Field>
                <Field label="납부자명">
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
                  {saving ? "처리 중" : "수납 기록"}
                </button>
              </fieldset>
              <p className="mt-2 text-xs text-[#68756c]">
                현재 잔액까지만 이 대상에 배분하고, 초과 수납액은 거래처 미배분
                잔액으로 남습니다.
              </p>
            </form>
          ) : (
            <p className="text-xs font-semibold">
              {!paymentAllowed || remainingAmount == null
                ? "현재 새 수납을 확인할 수 없습니다."
                : "수납 완료"}
            </p>
          )}

          {message ? <p className="text-xs font-semibold">{message}</p> : null}

          <div>
            <p className="mb-1.5 text-xs font-bold">
              수납 이력
              {events ? ` · 총 ${events.totalElements.toLocaleString()}건` : ""}
            </p>
            {eventsQuery.isError ? (
              <p role="alert" className="text-xs text-red-700">
                수납 이력을 불러오지 못했습니다.{" "}
                <button
                  type="button"
                  className="underline"
                  onClick={() => void eventsQuery.refetch()}
                >
                  다시 불러오기
                </button>
              </p>
            ) : eventsQuery.isPending ? (
              <p className="text-xs text-[#68756c]">
                수납 이력을 불러오는 중입니다.
              </p>
            ) : events?.content.length ? (
              <div className="divide-y divide-[#edf0ec] rounded-md border border-[#e1e6df] text-xs">
                {events.content.map((event) => (
                  <div
                    key={event.id}
                    className="flex flex-wrap justify-between gap-2 px-3 py-2"
                  >
                    <span>
                      반영 {formatShortDate(event.eventDate)} ·{" "}
                      {event.paymentMethod || "방법 미기재"}
                      {event.depositorName ? ` · ${event.depositorName}` : ""}
                    </span>
                    <strong
                      className={
                        event.status === "CANCELLED"
                          ? "text-[#68756c]"
                          : undefined
                      }
                    >
                      {event.status === "CANCELLED" ? "배분 취소 · " : ""}
                      {event.amount.toLocaleString()}원
                    </strong>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-[#68756c]">수납 이력이 없습니다.</p>
            )}
            {events && events.totalPages > 1 ? (
              <PaginationControls
                pageCount={events.totalPages}
                pageIndex={historyPage ?? 0}
                onPageChange={(page) =>
                  writeUrlParams(
                    (params) => params.set("paymentPage", String(page)),
                    "push",
                  )
                }
              />
            ) : null}
          </div>
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
