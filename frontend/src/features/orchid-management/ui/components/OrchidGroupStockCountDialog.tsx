"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { OrchidGroup } from "@/entities/farm/types";
import { createUuid } from "@/shared/lib/id";
import { ApiError } from "@/shared/api/client";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import {
  countOrchidGroup,
  getStockCountContext,
  getStockCounts,
  type StockCountContext,
} from "../../api/orchidManagementApi";

export default function OrchidGroupStockCountDialog({
  orchidGroup,
  onClose,
  onSaved,
}: {
  orchidGroup: OrchidGroup;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [pending, setPending] = useState(false);
  const context = useQuery({
    queryKey: ["orchid-groups", orchidGroup.id, "stock-count-context"],
    queryFn: ({ signal }) => getStockCountContext(orchidGroup.id, signal),
    staleTime: 0,
    enabled: !pending,
  });
  const history = useQuery({
    queryKey: ["orchid-groups", orchidGroup.id, "stock-counts", 0, 20],
    queryFn: ({ signal }) => getStockCounts(orchidGroup.id, signal),
  });
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !pending) onClose();
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="max-h-[90dvh] overflow-y-auto"
        onEscapeKeyDown={(e) => {
          if (pending) e.preventDefault();
        }}
        onPointerDownOutside={(e) => {
          if (pending) e.preventDefault();
        }}
      >
        <DialogTitle>실사 수량 조정 · {orchidGroup.varietyName}</DialogTitle>
        <DialogDescription>
          지금 확인한 수량만 반영합니다. 과거 작업·손실·위치·상태는 변경하지
          않습니다.
        </DialogDescription>
        {context.isPending || context.isError ? (
          <button type="button" onClick={onClose}>
            닫기
          </button>
        ) : null}
        {context.isPending ? (
          <p role="status">현재 장부 확인 중</p>
        ) : context.isError ? (
          <div role="alert">
            <p>{context.error.message}</p>
            <button onClick={() => void context.refetch()}>다시 확인</button>
          </div>
        ) : (
          <StockCountForm
            key={context.data.stateRevision}
            groupId={orchidGroup.id}
            context={context.data}
            onPendingChange={setPending}
            onClose={onClose}
            onSaved={onSaved}
            onRefresh={() => void context.refetch()}
          />
        )}
        <section className="border-t pt-3 text-sm">
          <h3 className="font-semibold">최근 실사 감사 기록 · 최대 20건</h3>
          {history.isError ? (
            <div role="alert">
              <p>{history.error.message}</p>
              <button onClick={() => void history.refetch()}>다시 조회</button>
            </div>
          ) : history.isPending ? (
            <p>조회 중</p>
          ) : history.data.content.length === 0 ? (
            <p>실사 기록 없음</p>
          ) : (
            <ol className="space-y-2">
              {history.data.content.map((event) => (
                <li key={event.idempotencyKey}>
                  <p>
                    장부 {event.beforeQuantity} → 실사 {event.actualQuantity}분
                    · 차이 {event.difference! > 0 ? "+" : ""}
                    {event.difference}분
                  </p>
                  <p>
                    {event.countedDate} · {event.reason}{" "}
                    {event.worker ? `· ${event.worker}` : ""}
                  </p>
                  {event.memo ? <p>{event.memo}</p> : null}
                </li>
              ))}
            </ol>
          )}
        </section>
      </DialogContent>
    </Dialog>
  );
}

function StockCountForm({
  groupId,
  context,
  onPendingChange,
  onClose,
  onSaved,
  onRefresh,
}: {
  groupId: number;
  context: StockCountContext;
  onPendingChange: (pending: boolean) => void;
  onClose: () => void;
  onSaved: () => void;
  onRefresh: () => void;
}) {
  const queryClient = useQueryClient();
  const [quantity, setQuantity] = useState(String(context.quantity));
  const [reason, setReason] = useState("");
  const [worker, setWorker] = useState("");
  const [memo, setMemo] = useState("");
  const [key] = useState(createUuid);
  const [validation, setValidation] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: () =>
      countOrchidGroup(groupId, {
        idempotencyKey: key,
        expectedRevision: context.stateRevision!,
        countedDate: context.businessDate!,
        actualQuantity: Number(quantity),
        reason: reason.trim(),
        worker: worker.trim() || undefined,
        memo: memo.trim() || undefined,
      }),
    onMutate: () => onPendingChange(true),
    onSettled: () => onPendingChange(false),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["orchid-groups", groupId] }),
        queryClient.invalidateQueries({ queryKey: ["farm-status"] }),
      ]);
      onSaved();
      onClose();
    },
  });
  const stale =
    mutation.error instanceof ApiError &&
    mutation.error.code === "STOCK_COUNT_STALE";
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (
      !quantity.trim() ||
      !Number.isInteger(Number(quantity)) ||
      Number(quantity) < 0 ||
      !reason.trim()
    ) {
      setValidation("0 이상의 정수 수량과 실사 사유를 입력하세요.");
      return;
    }
    setValidation(null);
    mutation.mutate();
  }
  return (
    <form onSubmit={submit} className="space-y-3">
      <p>
        확인 업무일 {context.businessDate} · 현재 장부 {context.quantity}분
      </p>
      <p className="text-sm">
        차이가 생긴 원인을 모르면 ‘실사 차이, 원인 미확인’으로 기록하세요.
        폐기나 증식으로 자동 분류하지 않습니다.
      </p>
      {!context.adjustable ? (
        <p role="alert">이 묶음은 실사 수량을 조정할 수 없습니다.</p>
      ) : null}
      <fieldset
        disabled={mutation.isPending || !context.adjustable || stale}
        className="space-y-3"
      >
        <label className="block">
          실사 확인 수량
          <input
            className="block w-full rounded border p-2"
            type="number"
            min="0"
            step="1"
            required
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </label>
        <label className="block">
          실사 사유
          <textarea
            className="block w-full rounded border p-2"
            required
            maxLength={1000}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <label className="block">
          확인자
          <input
            className="block w-full rounded border p-2"
            maxLength={100}
            value={worker}
            onChange={(e) => setWorker(e.target.value)}
          />
        </label>
        <label className="block">
          메모
          <input
            className="block w-full rounded border p-2"
            maxLength={1000}
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
          />
        </label>
        <button
          className="rounded bg-[#159447] px-3 py-2 font-semibold text-white disabled:opacity-50"
          type="submit"
        >
          {mutation.isPending ? "반영 중" : "실사 수량 반영"}
        </button>
      </fieldset>
      {validation || mutation.error ? (
        <p role="alert">{validation ?? mutation.error?.message}</p>
      ) : null}
      {stale ? (
        <button type="button" onClick={onRefresh}>
          현재 장부 다시 확인 · 입력 초기화
        </button>
      ) : null}
      <button type="button" disabled={mutation.isPending} onClick={onClose}>
        닫기
      </button>
    </form>
  );
}
