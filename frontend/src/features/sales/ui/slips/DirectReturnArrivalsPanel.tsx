import type { UseQueryResult } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import type {
  DirectReturnArrivalState,
  DirectPendingReturn,
  DirectReturnArrival,
  DirectArrivalCancellationPayload,
} from "../../api/directChangeTypes";
const buttonClass =
  "rounded-md border border-[#d9e0d8] bg-white px-3 py-2 text-sm font-semibold disabled:opacity-50";
export function DirectReturnArrivalsPanel({
  query,
  page,
  blocked,
  onPage,
  onArrival,
  onCancel,
}: {
  query: UseQueryResult<DirectReturnArrivalState, Error>;
  page: number;
  blocked: boolean;
  onPage: (page: number) => void;
  onArrival: (pending: DirectPendingReturn) => void;
  onCancel: (arrival: DirectReturnArrival) => void;
}) {
  const data = query.data;
  return (
    <div className="space-y-4 text-sm">
      {query.isPending ? (
        <p role="status">반품 도착을 불러오는 중입니다.</p>
      ) : null}
      {query.error ? (
        <p role="alert">
          {query.error.message}{" "}
          <button onClick={() => void query.refetch()}>다시 조회</button>
        </p>
      ) : null}
      {data ? (
        <>
          <h3 className="font-semibold">반품 도착 대기</h3>
          {data.pendingReturns.length === 0 ? (
            <p>등록된 반품이 없습니다.</p>
          ) : (
            data.pendingReturns.map((pending) => (
              <div
                key={pending.returnChangeId}
                className="flex flex-wrap items-center justify-between gap-2 rounded border p-3"
              >
                <span>
                  {pending.itemName} · 반품 #{pending.returnChangeId} · 도착
                  대기 {pending.pendingQuantity.toLocaleString()}분
                </span>
                <button
                  className={buttonClass}
                  disabled={blocked || !pending.arrivalAllowed}
                  onClick={() => onArrival(pending)}
                >
                  실제 도착 등록
                </button>
              </div>
            ))
          )}
          <h3 className="font-semibold">실제 도착 이력</h3>
          {data.history.content.length === 0 ? (
            <p>기록된 실제 도착이 없습니다.</p>
          ) : (
            data.history.content.map((arrival) => (
              <div key={arrival.id} className="space-y-2 rounded border p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span>
                    {arrival.arrivalDate} · 반품 #{arrival.returnChangeId} ·{" "}
                    {arrival.quantity.toLocaleString()}분
                    {arrival.canceledAt ? " · 도착 취소됨" : ""}
                  </span>
                  <button
                    className={buttonClass}
                    disabled={blocked || !arrival.cancellationAllowed}
                    onClick={() => onCancel(arrival)}
                  >
                    도착 취소 정정
                  </button>
                </div>
                <p className="text-xs text-[#68756c]">
                  {arrival.recordOnly
                    ? "재고 반영 없이 기록만 등록"
                    : `생성 난 묶음 #${arrival.orchidGroupId}`}
                </p>
                {arrival.cancellationReason ? (
                  <p>취소 사유: {arrival.cancellationReason}</p>
                ) : null}
              </div>
            ))
          )}
          {data.history.totalPages > 1 ? (
            <nav
              aria-label="반품 도착 이력 페이지"
              className="flex justify-end gap-3"
            >
              <button
                className={buttonClass}
                disabled={page <= 0 || query.isFetching}
                onClick={() => onPage(page - 1)}
              >
                이전
              </button>
              <span>
                {page + 1} / {data.history.totalPages}
              </span>
              <button
                className={buttonClass}
                disabled={
                  page + 1 >= data.history.totalPages || query.isFetching
                }
                onClick={() => onPage(page + 1)}
              >
                다음
              </button>
            </nav>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
export function DirectArrivalCancelForm({
  arrival,
  saving,
  blocked,
  error,
  onClose,
  onSubmit,
}: {
  arrival: DirectReturnArrival;
  saving: boolean;
  blocked: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (payload: DirectArrivalCancellationPayload) => Promise<void>;
}) {
  const { businessDate } = useRuntimeContext();
  const [date, setDate] = useState(businessDate);
  const [reason, setReason] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (blocked || saving) return;
    try {
      await onSubmit({ correctionDate: date, reason: reason.trim() });
    } catch {
      /* Hook retains uncertain input. */
    }
  }
  return (
    <form className="space-y-3 p-5" onSubmit={(event) => void submit(event)}>
      <h3 className="font-semibold">도착 취소 정정</h3>
      <p>
        도착 #{arrival.id} · {arrival.quantity}분
        {arrival.recordOnly
          ? " · 도착 기록만 취소합니다."
          : " · 새 묶음의 재고 효과도 취소합니다. 후속 사용을 먼저 정정해야 합니다."}
      </p>
      <fieldset disabled={saving || blocked} className="space-y-3">
        <label className="block">
          정정일
          <input
            autoFocus
            required
            className="mt-1 h-9 w-full rounded border px-2"
            type="date"
            min={arrival.arrivalDate}
            value={date}
            onChange={(e) => setDate(e.target.value)}
          />
        </label>
        <label className="block">
          도착 취소 사유
          <textarea
            required
            maxLength={1000}
            className="mt-1 min-h-20 w-full rounded border p-2"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
      </fieldset>
      {error ? (
        <p role="alert" className="text-red-700">
          {error}
        </p>
      ) : null}
      <div className="flex justify-end gap-2">
        <button
          className={buttonClass}
          type="button"
          disabled={saving}
          onClick={onClose}
        >
          닫기
        </button>
        <button
          className={buttonClass}
          type="submit"
          disabled={saving || blocked}
        >
          도착 취소 저장
        </button>
      </div>
    </form>
  );
}
