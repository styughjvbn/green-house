import type { FormEvent } from "react";
import { X } from "lucide-react";
import type { AuctionLot } from "@/entities/farm/types";
import { auctionInspectionLabel } from "../../lib/auctionDisplay";

export function AuctionQuantityAdjustDialog({
  lot,
  loading,
  onClose,
  onSubmit,
}: {
  lot: AuctionLot;
  loading: boolean;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void | Promise<void>;
}) {
  async function submitAdjustment(event: FormEvent<HTMLFormElement>) {
    await onSubmit(event);
    onClose();
  }

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-xl flex-col rounded-md bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="수량 보정"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex shrink-0 items-start justify-between gap-3 border-b border-[#edf0ec] p-5">
          <div>
            <p className="text-sm font-semibold text-[#3d6f91]">
              LOT #{lot.id}
            </p>
            <h2 className="mt-1 text-xl font-semibold">수량 보정</h2>
          </div>
          <button
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-[#d9dfda] text-[#435047] hover:bg-[#f4f7f3]"
            type="button"
            onClick={onClose}
            aria-label="닫기"
          >
            <X className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
          </button>
        </div>

        <form className="space-y-3 p-5" onSubmit={submitAdjustment}>
          <p className="text-xs text-[#68756c]">
            낙찰 + 대기 + 반환 = 출하 수량
          </p>
          <div className="grid grid-cols-3 gap-2">
            <NumberInput
              name="soldQuantity"
              label="낙찰"
              value={lot.soldQuantity}
            />
            <NumberInput
              name="waitingQuantity"
              label="대기"
              value={lot.waitingQuantity}
            />
            <NumberInput
              name="returnedQuantity"
              label="반환"
              value={lot.returnedQuantity}
            />
          </div>
          <label className="block text-xs font-semibold">
            작업자
            <input
              name="worker"
              className="mt-1 h-8 w-full rounded border border-[#d9e0d8] px-2 text-sm"
            />
          </label>
          <label className="block text-xs font-semibold">
            보정 사유
            <textarea
              name="memo"
              required
              className="mt-1 min-h-16 w-full rounded border border-[#d9e0d8] p-2 text-sm"
            />
          </label>
          <p className="text-xs text-[#68756c]">
            검수 {auctionInspectionLabel(lot.inspectionStatus)} · 매출{" "}
            {lot.totalAmount.toLocaleString()}원
          </p>
          <div className="flex justify-end gap-2">
            <button
              className="h-9 rounded-md border border-[#d7ddd4] px-4 text-sm font-semibold"
              type="button"
              onClick={onClose}
            >
              취소
            </button>
            <button
              className="h-9 rounded-md bg-[#159447] px-4 text-sm font-bold text-white disabled:opacity-50"
              type="submit"
              disabled={loading}
            >
              수량 보정 저장
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function NumberInput({
  name,
  label,
  value,
}: {
  name: string;
  label: string;
  value: number;
}) {
  return (
    <label className="text-xs font-semibold">
      {label}
      <input
        name={name}
        type="number"
        min={0}
        defaultValue={value}
        className="mt-1 h-8 w-full rounded border border-[#d9e0d8] px-2 text-right text-sm"
      />
    </label>
  );
}
