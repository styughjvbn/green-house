"use client";

import {
  useState,
  useRef,
  useEffect,
  useSyncExternalStore,
  type FormEvent,
} from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { ApiError } from "@/shared/api/client";
import { useUrlSearchParamsWriter } from "@/shared/lib/useUrlSearchParamsWriter";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import { createUuid } from "@/shared/lib/id";
import { PaginationControls } from "@/shared/ui/PaginationControls";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import { BusinessPartnerSelect } from "../common/BusinessPartnerSelect";
import {
  getUnassignedReceiptPage,
  getPaymentBalance,
  receiveUnassignedPayment,
  cancelUnassignedPayment,
} from "../../api/salesApi";
import { readReceiptRouteState } from "../../lib/salesRouteParams";
import { salesQueryKeys } from "../../model/salesQueryKeys";
import {
  createReceiptRequests,
  type ReceiptRequest,
} from "../../lib/receiptRequest";

const requests = createReceiptRequests(createUuid, () =>
  typeof window === "undefined" ? null : window.sessionStorage,
);
const inputClass =
  "mt-1 h-10 w-full rounded border border-[#d7ddd8] bg-white px-3";

export function UnassignedReceiptView() {
  const params = useSearchParams();
  const write = useUrlSearchParamsWriter();
  const { partnerId: selectedId } = readReceiptRouteState(params);
  return (
    <div className="min-h-0 flex-1 space-y-4 overflow-auto">
      <BusinessPartnerSelect
        label="수납 거래처"
        value={selectedId ? String(selectedId) : ""}
        onChange={(value) =>
          write((p) => {
            if (value) p.set("receiptPartnerId", value);
            else p.delete("receiptPartnerId");
            p.delete("receiptPage");
          }, "push")
        }
      />
      {selectedId ? (
        <ReceiptPanel key={selectedId} partnerId={selectedId} />
      ) : (
        <p>수납할 거래처를 선택하세요.</p>
      )}
    </div>
  );
}

function ReceiptPanel({ partnerId }: { partnerId: number }) {
  const params = useSearchParams();
  const write = useUrlSearchParamsWriter();
  const { page } = readReceiptRouteState(params);
  const cache = useQueryClient();
  const { businessDate } = useRuntimeContext();
  const pending = useSyncExternalStore(
    requests.subscribe,
    () => requests.read(partnerId),
    () => null,
  );
  const history = useQuery({
    queryKey: salesQueryKeys.payments.unassignedPage(partnerId, page),
    queryFn: ({ signal }) => getUnassignedReceiptPage(partnerId, page, signal),
  });
  const balance = useQuery({
    queryKey: salesQueryKeys.payments.balance(partnerId),
    queryFn: ({ signal }) => getPaymentBalance(partnerId, signal),
  });
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(businessDate);
  const [depositor, setDepositor] = useState("");
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const returnFocus = useRef<HTMLButtonElement | null>(null);
  const historyTitle = useRef<HTMLHeadingElement | null>(null);
  const [cancelId, setCancelId] = useState<number | null>(null);
  const [reason, setReason] = useState("");
  const [correctionDate, setCorrectionDate] = useState(businessDate);

  useEffect(() => {
    if (history.data && page >= Math.max(1, history.data.totalPages)) {
      const lastPage = Math.max(0, history.data.totalPages - 1);
      write((p) => p.set("receiptPage", String(lastPage)));
    }
  }, [history.data, page, write]);

  async function execute(request: ReceiptRequest) {
    setSaving(true);
    setError(null);
    try {
      if (request.operation === "RECEIVE")
        await receiveUnassignedPayment(partnerId, request.payload);
      else
        await cancelUnassignedPayment(
          partnerId,
          request.receiptId,
          request.payload,
        );
      requests.complete(partnerId, request.payload.idempotencyKey);
      setCancelId(null);
      setReason("");
      setAmount("");
      setMemo("");
      await Promise.all([
        cache.invalidateQueries({
          queryKey: salesQueryKeys.payments.unassigned(partnerId),
        }),
        cache.invalidateQueries({
          queryKey: salesQueryKeys.payments.balance(partnerId),
        }),
        cache.invalidateQueries({ queryKey: salesQueryKeys.partners.all }),
      ]);
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        [400, 404, 409].includes(failure.status) &&
        failure.code !== "IDEMPOTENCY_KEY_REUSED"
      )
        requests.complete(partnerId, request.payload.idempotencyKey);
      setError(
        failure instanceof Error
          ? failure.message
          : "수납을 저장하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }
  async function receive(event: FormEvent) {
    event.preventDefault();
    const value = Number(amount);
    if (!Number.isSafeInteger(value) || value <= 0) {
      setError("입금액은 양의 정수로 입력하세요.");
      return;
    }
    await execute(
      requests.prepare(partnerId, {
        operation: "RECEIVE",
        payload: {
          amount: value,
          paymentDate: date,
          paymentMethod: "계좌이체",
          worker: null,
          depositorName: depositor.trim() || null,
          memo: memo.trim() || null,
        },
      }),
    );
  }
  return (
    <div className="space-y-4">
      <p className="text-sm">
        대상 미지정 수납을 기록합니다. 전표의 입금액에는 배분 후 반영됩니다.
      </p>
      {balance.data ? (
        <p className="font-bold">
          미배분 수납 {balance.data.unappliedPaymentAmount.toLocaleString()}원
        </p>
      ) : (
        <p>
          {balance.isError ? "잔액을 불러오지 못했습니다." : "잔액 확인 중"}
        </p>
      )}
      {pending ? (
        <div role="status" className="rounded border p-3">
          처리 결과 확인이 필요합니다. 기존 입력으로 다시 확인하세요.
          <button
            type="button"
            disabled={saving}
            onClick={() => void execute(pending)}
            className="ml-3 rounded border px-3 py-2"
          >
            같은 요청 다시 확인
          </button>
        </div>
      ) : null}
      {error ? (
        <p role="alert" className="text-red-700">
          {error}
        </p>
      ) : null}
      <form onSubmit={receive}>
        <fieldset
          disabled={saving || !!pending}
          className="grid gap-3 sm:grid-cols-2"
        >
          <label>
            입금액
            <input
              className={inputClass}
              type="number"
              min="1"
              max={Number.MAX_SAFE_INTEGER}
              step="1"
              required
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </label>
          <label>
            입금일
            <input
              className={inputClass}
              type="date"
              required
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </label>
          <label>
            입금자
            <input
              className={inputClass}
              maxLength={100}
              value={depositor}
              onChange={(e) => setDepositor(e.target.value)}
            />
          </label>
          <label>
            메모
            <input
              className={inputClass}
              maxLength={1000}
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
            />
          </label>
          <button
            type="submit"
            className="rounded bg-[#159447] px-4 py-2 font-bold text-white"
          >
            {saving ? "저장 중" : "수납 기록"}
          </button>
        </fieldset>
      </form>
      <h2 ref={historyTitle} tabIndex={-1} className="font-bold">
        수납·정정 내역
      </h2>
      {history.isError ? (
        <p role="alert">
          내역을 불러오지 못했습니다.{" "}
          <button onClick={() => void history.refetch()}>다시 조회</button>
        </p>
      ) : history.isPending ? (
        <p>내역 확인 중</p>
      ) : (
        <>
          {history.data.content.length === 0 ? (
            <p>수납 내역이 없습니다.</p>
          ) : (
            <ul className="divide-y rounded border">
              {history.data.content.map((item) => (
                <li
                  key={item.id}
                  className="flex flex-wrap items-center gap-3 p-3"
                >
                  <span>{item.eventDate}</span>
                  <span>
                    {item.eventType === "ADJUSTMENT" ? "오입력 취소" : "수납"}{" "}
                    {item.amount.toLocaleString()}원
                    {item.status === "CANCELLED" ? " (취소됨)" : ""}
                  </span>
                  <span>{item.memo}</span>
                  {item.parentEventId ? (
                    <span>원본 #{item.parentEventId}</span>
                  ) : null}
                  <button
                    disabled={
                      saving || !!pending || !item.unassignedCancellationAllowed
                    }
                    type="button"
                    className="ml-auto rounded border px-3 py-2 disabled:opacity-40"
                    onClick={(event) => {
                      returnFocus.current = event.currentTarget;
                      setReason("");
                      setCorrectionDate(businessDate);
                      setCancelId(item.id);
                    }}
                  >
                    오입력 취소
                  </button>
                </li>
              ))}
            </ul>
          )}
          <PaginationControls
            pageIndex={page}
            pageCount={history.data.totalPages}
            onPageChange={(next) =>
              write((p) => p.set("receiptPage", String(next)), "push")
            }
          />
        </>
      )}
      <Dialog
        open={cancelId != null}
        onOpenChange={(open) => {
          if (!open && !saving) setCancelId(null);
        }}
      >
        <DialogContent
          showCloseButton={false}
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            const target = returnFocus.current;
            if (target?.isConnected && !target.disabled) target.focus();
            else historyTitle.current?.focus();
          }}
          onEscapeKeyDown={(e) => {
            if (saving) e.preventDefault();
          }}
          onPointerDownOutside={(e) => {
            if (saving) e.preventDefault();
          }}
        >
          <DialogTitle>수납 오입력 취소</DialogTitle>
          <DialogDescription>
            입력 실수를 정정합니다. 실제 돈을 돌려준 기록은 별도입니다.
          </DialogDescription>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (cancelId && reason.trim())
                void execute(
                  requests.prepare(partnerId, {
                    operation: "CANCEL",
                    receiptId: cancelId,
                    payload: { correctionDate, reason: reason.trim() },
                  }),
                );
            }}
          >
            <fieldset disabled={saving || !!pending} className="space-y-3">
              <label className="block">
                정정일
                <input
                  className={inputClass}
                  type="date"
                  required
                  value={correctionDate}
                  onChange={(e) => setCorrectionDate(e.target.value)}
                />
              </label>
              <label className="block">
                정정 사유
                <input
                  className={inputClass}
                  required
                  maxLength={1000}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </label>
              <button className="rounded border px-4 py-2" type="submit">
                오입력 취소 확정
              </button>
            </fieldset>
            {error ? (
              <p role="alert" className="text-red-700">
                {error}
              </p>
            ) : null}
            <button
              className="mt-3 rounded border px-4 py-2"
              disabled={saving}
              type="button"
              onClick={() => setCancelId(null)}
            >
              닫기
            </button>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
