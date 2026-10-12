import { useRef, useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { CircleDollarSign } from "lucide-react";
import { DetailActionButton } from "@/shared/ui/DetailCard";
import type { Route } from "next";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import type {
  DirectChangeHistory,
  DirectChangeType,
  DirectPricePortion,
} from "../../api/directChangeTypes";
import { useDirectSaleChanges } from "../../model/useDirectSaleChanges";
import type { DirectChangeInput } from "../../lib/directChangeRequests";

const labels: Record<DirectChangeType, string> = {
  PRICE_ADJUSTMENT: "가격 조정",
  RETURN: "반품",
  CANCEL: "처리 취소",
};
const inputClass =
  "mt-1 h-9 w-full rounded-md border border-[#d9e0d8] bg-white px-2 text-sm";
const buttonClass =
  "rounded-md border border-[#d9e0d8] bg-white px-3 py-2 text-sm font-semibold disabled:opacity-50";
type Action =
  | { type: "PRICE_ADJUSTMENT" | "RETURN" }
  | { type: "CANCEL"; change: DirectChangeHistory };

export function useDirectSaleChangeActions(documentId: number | null) {
  const params = useSearchParams();
  const router = useRouter();
  const rawPage = Number(params.get("changePage") ?? "0");
  const page =
    Number.isSafeInteger(rawPage) && rawPage >= 0 && rawPage <= 2147483647
      ? rawPage
      : 0;
  const changes = useDirectSaleChanges(
    documentId ?? 0,
    page,
    documentId != null,
  );
  const [action, setAction] = useState<Action | { type: "HISTORY" } | null>(
    null,
  );
  const trigger = useRef<HTMLElement | null>(null);
  const header = useRef<HTMLElement | null>(null);
  function open(next: Action | { type: "HISTORY" }) {
    const focused = document.activeElement;
    if (focused instanceof HTMLElement) {
      trigger.current = focused;
      header.current = focused.closest("header");
    }
    setAction(next);
  }
  function restoreFocus(event: Event) {
    const visible = (element: HTMLElement) =>
      element.isConnected &&
      !element.closest('[aria-hidden="true"]') &&
      element.getClientRects().length > 0;
    const original = trigger.current;
    const fallback = Array.from(
      header.current?.querySelectorAll<HTMLButtonElement>("button") ?? [],
    ).find(visible);
    const target = original && visible(original) ? original : fallback;
    if (target) {
      event.preventDefault();
      target.focus();
    }
  }
  function goPage(next: number) {
    const query = new URLSearchParams(params.toString());
    if (next === 0) query.delete("changePage");
    else query.set("changePage", String(next));
    router.replace(`/sales/slips?${query}` as Route, { scroll: false });
  }
  const data = changes.query.data;
  const canAdjust = data?.availableTypes.includes("PRICE_ADJUSTMENT") === true;
  const canReturn = data?.availableTypes.includes("RETURN") === true;
  const canOpen =
    documentId != null &&
    (canAdjust ||
      canReturn ||
      !!changes.pending ||
      !!changes.query.error ||
      (data?.history.totalElements ?? 0) > 0);
  const actions = canOpen ? (
    <DetailActionButton
      icon={CircleDollarSign}
      disabled={changes.saving}
      onClick={() =>
        open(
          canAdjust && !changes.pending
            ? { type: "PRICE_ADJUSTMENT" }
            : { type: "HISTORY" },
        )
      }
    >
      가격 조정 및 반품
    </DetailActionButton>
  ) : null;
  const dialogs = (
    <>
      {action ? (
        <Dialog
          open
          onOpenChange={(opened) => {
            if (!opened && !changes.saving) setAction(null);
          }}
        >
          <DialogContent
            className="flex max-h-[calc(100dvh-2rem)] max-w-3xl flex-col rounded-md"
            showCloseButton={false}
            onCloseAutoFocus={restoreFocus}
          >
            <div className="shrink-0 border-b border-[#edf0ec] p-5">
              <DialogTitle className="text-xl font-semibold">
                가격 조정 및 반품
              </DialogTitle>
              <DialogDescription className="mt-2 text-sm text-[#68756c]">
                {action.type === "HISTORY"
                  ? "현재 가격별 수량과 가격 조정·반품·취소 기록을 확인합니다."
                  : action.type === "RETURN"
                    ? "반품 수량과 금액을 기록합니다. 실제 농장 도착·재고 반영·환불은 별도 처리입니다."
                    : action.type === "CANCEL"
                      ? "원기록을 보존하고 취소 이력을 추가합니다."
                      : "원품목은 유지하고 선택한 수량의 단가를 조정합니다."}
              </DialogDescription>
            </div>
            <div
              className="flex shrink-0 flex-wrap gap-2 border-b border-[#edf0ec] px-5 py-3"
              role="group"
              aria-label="가격 조정 및 반품 메뉴"
            >
              <button
                type="button"
                className={`${buttonClass} aria-pressed:border-green-600 aria-pressed:bg-green-50 aria-pressed:text-green-800`}
                aria-pressed={action.type === "PRICE_ADJUSTMENT"}
                disabled={!canAdjust || changes.saving || !!changes.pending}
                onClick={() => setAction({ type: "PRICE_ADJUSTMENT" })}
              >
                가격 조정
              </button>
              <button
                type="button"
                className={`${buttonClass} aria-pressed:border-green-600 aria-pressed:bg-green-50 aria-pressed:text-green-800`}
                aria-pressed={action.type === "RETURN"}
                disabled={!canReturn || changes.saving || !!changes.pending}
                onClick={() => setAction({ type: "RETURN" })}
              >
                반품 등록
              </button>
              <button
                type="button"
                className={`${buttonClass} aria-pressed:border-green-600 aria-pressed:bg-green-50 aria-pressed:text-green-800`}
                aria-pressed={
                  action.type === "HISTORY" || action.type === "CANCEL"
                }
                disabled={changes.saving}
                onClick={() => setAction({ type: "HISTORY" })}
              >
                변경 이력
              </button>
            </div>
            {action.type === "HISTORY" ? (
              <>
                <div className="min-h-0 space-y-3 overflow-y-auto p-5 text-sm">
                  {changes.query.isPending ? (
                    <p>가격 조정·반품을 불러오는 중입니다.</p>
                  ) : null}
                  {changes.query.error ? (
                    <div role="alert">
                      <p>{changes.query.error.message}</p>
                      <button
                        className={buttonClass}
                        onClick={() => void changes.query.refetch()}
                      >
                        다시 불러오기
                      </button>
                    </div>
                  ) : null}
                  {changes.error ? (
                    <p role="alert" className="text-red-700">
                      {changes.error.message}
                    </p>
                  ) : null}
                  {changes.pending ? (
                    <div className="rounded-md bg-amber-50 p-3">
                      <p>
                        이전 {labels[changes.pending.operation]} 요청의 결과를
                        확인해야 합니다. 원래 입력으로 다시 확인합니다.
                      </p>
                      <button
                        className={`${buttonClass} mt-2`}
                        disabled={changes.saving}
                        onClick={() => {
                          void changes.retry().catch(() => undefined);
                        }}
                      >
                        요청 결과 확인
                      </button>
                    </div>
                  ) : null}
                  {data ? (
                    <>
                      <p className="text-[#68756c]">
                        원판매 금액 {data.originalAmount.toLocaleString()}원 ·
                        현재 거래금액 {data.currentAmount.toLocaleString()}원
                      </p>
                      <p className="text-xs text-[#68756c]">
                        전표 품목에서도 현재 가격 구성을 확인할 수 있습니다.
                        원판매 기록은 보존됩니다.
                      </p>
                      <div className="overflow-auto">
                        <table className="w-full text-left">
                          <thead>
                            <tr className="border-b border-[#edf0ec]">
                              <th className="py-2">품목</th>
                              <th>가격 구간</th>
                              <th className="text-right">수량</th>
                              <th className="text-right">단가</th>
                              <th className="text-right">금액</th>
                            </tr>
                          </thead>
                          <tbody>
                            {data.portions.map((portion) => (
                              <tr
                                className="border-b border-[#edf0ec]"
                                key={portionKey(portion)}
                              >
                                <td className="py-2">
                                  {portion.itemName}
                                  {portion.spec ? ` · ${portion.spec}` : ""}
                                </td>
                                <td>
                                  {portion.sourceChangeId == null
                                    ? "원가격"
                                    : `조정 #${portion.sourceChangeId}`}
                                </td>
                                <td className="text-right">
                                  {portion.quantity.toLocaleString()}
                                </td>
                                <td className="text-right">
                                  {portion.unitPrice.toLocaleString()}
                                </td>
                                <td className="text-right">
                                  {portion.amount.toLocaleString()}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                        {data.portions.length === 0 ? (
                          <p className="py-2">표시할 가격 구간이 없습니다.</p>
                        ) : null}
                      </div>
                      <div className="space-y-2">
                        {data.history.content.length === 0 ? (
                          <p className="text-[#68756c]">
                            가격 조정·반품 이력이 없습니다.
                          </p>
                        ) : null}
                        {data.history.content.map((change) => (
                          <div
                            key={change.id}
                            className="rounded-md border border-[#edf0ec] p-3"
                          >
                            <div className="flex flex-wrap justify-between gap-2">
                              <strong>
                                {labels[change.type]} #{change.id}
                                {change.canceled ? " · 취소됨" : ""}
                              </strong>
                              <span>{change.changeDate}</span>
                            </div>
                            <p className="mt-1">
                              {change.itemName} · {change.quantity}개
                              {change.type === "PRICE_ADJUSTMENT"
                                ? ` · ${change.previousUnitPrice.toLocaleString()}원 → ${change.nextUnitPrice.toLocaleString()}원`
                                : ""}{" "}
                              · {change.amountDelta > 0 ? "+" : ""}
                              {change.amountDelta.toLocaleString()}원
                            </p>
                            <p className="mt-1 text-[#68756c]">
                              {change.reason}
                              {change.canceledChangeId == null
                                ? ""
                                : ` · 원이력 #${change.canceledChangeId}`}
                            </p>
                            {change.cancelAllowed ? (
                              <button
                                className={`${buttonClass} mt-2`}
                                disabled={changes.saving || !!changes.pending}
                                onClick={() =>
                                  setAction({ type: "CANCEL", change })
                                }
                              >
                                처리 취소
                              </button>
                            ) : null}
                          </div>
                        ))}
                      </div>
                      {data.history.totalPages > 1 ? (
                        <div className="flex items-center justify-end gap-2">
                          <button
                            className={buttonClass}
                            disabled={page <= 0}
                            onClick={() => goPage(page - 1)}
                          >
                            이전
                          </button>
                          <span>
                            {page + 1} / {data.history.totalPages}
                          </span>
                          <button
                            className={buttonClass}
                            disabled={page + 1 >= data.history.totalPages}
                            onClick={() => goPage(page + 1)}
                          >
                            다음
                          </button>
                        </div>
                      ) : null}
                      {/* TODO: 수납 비활성화 — 전표 등록 마무리 후 연결 예정. 실제 환불 처리와 수납 이력은 노출하지 않는다. */}
                    </>
                  ) : null}
                </div>
                <div className="flex shrink-0 justify-end border-t border-[#edf0ec] p-4">
                  <button
                    className={buttonClass}
                    disabled={changes.saving}
                    type="button"
                    onClick={() => setAction(null)}
                  >
                    닫기
                  </button>
                </div>
              </>
            ) : data ? (
              <div className="min-h-0 overflow-y-auto">
                <DirectSaleChangeForm
                  key={
                    action.type === "CANCEL"
                      ? `cancel-${action.change.id}`
                      : action.type
                  }
                  action={action}
                  portions={data.portions}
                  saving={changes.saving}
                  blocked={!!changes.pending}
                  error={changes.error?.message}
                  onClose={() => setAction(null)}
                  onSubmit={async (input) => {
                    await changes.submit(input);
                    setAction({ type: "HISTORY" });
                  }}
                />
              </div>
            ) : null}
          </DialogContent>
        </Dialog>
      ) : null}
    </>
  );
  return { actions, dialogs, priceQuery: changes.query };
}

function portionKey(portion: DirectPricePortion) {
  return `${portion.itemId}:${portion.sourceChangeId ?? "original"}`;
}

function DirectSaleChangeForm({
  action,
  portions,
  saving,
  blocked,
  error,
  onClose,
  onSubmit,
}: {
  action: Action;
  portions: DirectPricePortion[];
  saving: boolean;
  blocked: boolean;
  error?: string;
  onClose: () => void;
  onSubmit: (input: DirectChangeInput) => Promise<void>;
}) {
  const { businessDate } = useRuntimeContext();
  const [selected, setSelected] = useState(
    portions[0] ? portionKey(portions[0]) : "",
  );
  const [quantity, setQuantity] = useState("1");
  const [unitPrice, setUnitPrice] = useState("");
  const [date, setDate] = useState(businessDate);
  const [reason, setReason] = useState("");
  const portion = portions.find((value) => portionKey(value) === selected);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving || blocked) return;
    const payload = { changeDate: date, reason: reason.trim() };
    try {
      if (action.type === "CANCEL")
        await onSubmit({
          operation: "CANCEL",
          changeId: action.change.id,
          payload,
        });
      else if (portion)
        await onSubmit({
          operation: action.type,
          payload: {
            ...payload,
            itemId: portion.itemId,
            sourceChangeId: portion.sourceChangeId ?? undefined,
            quantity: Number(quantity),
            unitPrice:
              action.type === "PRICE_ADJUSTMENT"
                ? Number(unitPrice)
                : undefined,
          },
        });
    } catch {
      /* The hook retains uncertain requests and exposes server errors. */
    }
  }
  return (
    <form className="space-y-3 p-5" onSubmit={(event) => void submit(event)}>
      {action.type === "CANCEL" ? (
        <h3 className="font-semibold">처리 취소</h3>
      ) : null}
      <fieldset disabled={saving || blocked} className="space-y-3">
        {action.type !== "CANCEL" ? (
          <>
            <label className="block text-sm font-semibold">
              품목 · 가격 구간
              <select
                className={inputClass}
                value={selected}
                onChange={(event) => {
                  setSelected(event.target.value);
                  setQuantity("1");
                }}
                required
              >
                {portions.map((value) => (
                  <option key={portionKey(value)} value={portionKey(value)}>
                    {value.itemName}
                    {value.spec ? ` · ${value.spec}` : ""} ·{" "}
                    {value.sourceChangeId == null
                      ? "원가격"
                      : `조정 #${value.sourceChangeId}`}{" "}
                    · {value.unitPrice.toLocaleString()}원 · {value.quantity}개
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm font-semibold">
              {action.type === "RETURN" ? "반품 수량" : "조정 수량"}
              <input
                className={inputClass}
                type="number"
                min="1"
                max={portion?.quantity}
                step="1"
                required
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
              />
            </label>
            {action.type === "PRICE_ADJUSTMENT" ? (
              <label className="block text-sm font-semibold">
                변경 단가
                <input
                  className={inputClass}
                  type="number"
                  min="0"
                  max="2147483647"
                  step="1"
                  required
                  value={unitPrice}
                  onChange={(event) => setUnitPrice(event.target.value)}
                />
              </label>
            ) : null}
          </>
        ) : (
          <p>
            {action.change.itemName} · {labels[action.change.type]} #
            {action.change.id}
          </p>
        )}
        <label className="block text-sm font-semibold">
          처리일
          <input
            className={inputClass}
            type="date"
            autoFocus={action.type === "CANCEL"}
            required
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </label>
        <label className="block text-sm font-semibold">
          사유
          <textarea
            className="mt-1 min-h-20 w-full rounded-md border border-[#d9e0d8] p-2 text-sm"
            required
            maxLength={500}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </label>
      </fieldset>
      {error ? (
        <p role="alert" className="text-sm text-red-700">
          {error}
        </p>
      ) : null}
      {blocked ? (
        <p className="text-sm text-amber-800">
          변경 이력에서 이전 요청의 결과를 확인하세요.
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
          className="rounded-md bg-[#159447] px-4 py-2 font-semibold text-white disabled:opacity-50"
          type="submit"
          disabled={saving || blocked}
        >
          {saving ? "저장 중" : "기록"}
        </button>
      </div>
    </form>
  );
}
