import { useState, type FormEvent } from "react";
import { RotateCcw } from "lucide-react";
import type { AuctionLot } from "@/entities/farm/types";
import { auctionInspectionLabel } from "../../lib/auctionDisplay";
import { StatusBadge } from "./AuctionLotList";

export function AuctionLotDetail({
  lot,
  loading,
  onConfirmReturn,
  onAdjust,
}: {
  lot: AuctionLot | null;
  loading: boolean;
  onConfirmReturn: (returnedQuantity: number) => Promise<void>;
  onAdjust: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const [showReturnConfirmation, setShowReturnConfirmation] = useState(false);
  const [returnQuantity, setReturnQuantity] = useState(1);

  if (!lot) {
    return (
      <section className="rounded-md border border-[#dfe5dc] bg-white p-8 text-center text-sm text-[#6c786f]">
        조회할 lot을 선택하세요.
      </section>
    );
  }

  return (
    <section className="min-w-0 rounded-md border border-[#dfe5dc] bg-white shadow-sm">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-[#e7ebe5] px-4 py-3">
        <div>
          <p className="text-xs font-semibold text-[#6b786f]">LOT #{lot.id}</p>
          <h2 className="text-base font-bold">
            {lot.varietyName} · {lot.auctionMarket}
          </h2>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge status={lot.currentStatus} />
          {lot.currentStatus === "RETURN_INFERRED" ||
          lot.currentStatus === "PARTIALLY_RETURNED" ? (
            <button
              className="inline-flex h-8 items-center gap-1.5 rounded-md border border-[#d7ded5] px-3 text-xs font-semibold disabled:opacity-50"
              type="button"
              disabled={loading || lot.waitingQuantity === 0}
              onClick={() => {
                setReturnQuantity(lot.waitingQuantity);
                setShowReturnConfirmation((current) => !current);
              }}
            >
              <RotateCcw className="h-3.5 w-3.5" />
              반환 확인
            </button>
          ) : null}
        </div>
      </header>

      {showReturnConfirmation ? (
        <form
          className="flex flex-wrap items-end gap-3 border-b border-[#e7ebe5] bg-[#fff9ed] px-4 py-3"
          onSubmit={async (event) => {
            event.preventDefault();
            await onConfirmReturn(returnQuantity);
            setShowReturnConfirmation(false);
          }}
        >
          <label className="text-xs font-semibold text-[#5a4932]">
            반환 확인 수량
            <input
              className="mt-1 block h-9 w-36 rounded-md border border-[#d8c7a8] bg-white px-3 text-right text-sm"
              type="number"
              min={1}
              max={lot.waitingQuantity}
              required
              value={returnQuantity}
              onChange={(event) =>
                setReturnQuantity(Number(event.target.value))
              }
            />
          </label>
          <div className="min-w-40 text-xs text-[#6b5b44]">
            <p>현재 대기 {lot.waitingQuantity.toLocaleString()}분</p>
            <p className="mt-1 font-bold">
              변경 상태:{" "}
              {returnQuantity === lot.waitingQuantity ? "반환완료" : "부분반환"}
            </p>
          </div>
          <button
            className="h-9 rounded-md bg-[#159447] px-4 text-sm font-bold text-white disabled:opacity-50"
            type="submit"
            disabled={
              loading ||
              returnQuantity < 1 ||
              returnQuantity > lot.waitingQuantity
            }
          >
            반환 확인 저장
          </button>
        </form>
      ) : null}

      <div className="grid gap-3 p-4 lg:grid-cols-[minmax(0,1fr)_300px]">
        <div>
          <div className="grid grid-cols-4 gap-2 rounded-md bg-[#f7f9f6] p-3 text-center">
            <Quantity label="출하" value={lot.shippedQuantity} />
            <Quantity label="판매" value={lot.soldQuantity} />
            <Quantity label="대기" value={lot.waitingQuantity} />
            <Quantity label="반환" value={lot.returnedQuantity} />
          </div>
          <h3 className="mt-4 mb-2 text-sm font-bold">경매 진행 타임라인</h3>
          <ol className="space-y-2 border-l-2 border-[#dce9da] pl-4">
            <TimelineItem
              date={lot.shipmentDate}
              title={`경매장 출하 ${lot.shippedQuantity.toLocaleString()}분`}
              description={`${lot.boxes == null ? "상자 수 미상" : `${lot.boxes.toLocaleString()}상자`} · 출하등급 ${lot.shipmentGrade || "미지정"}`}
            />
            {lot.attempts.map((attempt) => (
              <TimelineItem
                key={attempt.id}
                date={attempt.auctionDate}
                title={`${attempt.attemptNo}차 경매 · ${attempt.attemptStatus}`}
                description={
                  attempt.resultLines.length > 0
                    ? attempt.resultLines
                        .map(
                          (line) =>
                            `${line.quantity.toLocaleString()}분 ${line.amount > 0 ? `${line.amount.toLocaleString()}원` : "유찰"}`,
                        )
                        .join(" / ")
                    : attempt.failedReason || "결과 없음"
                }
              />
            ))}
            {lot.statusHistory.map((history) => (
              <TimelineItem
                key={`history-${history.id}`}
                date={history.changedAt.slice(0, 10)}
                title={`상태 변경 · ${history.reason}`}
                description={history.memo || history.worker || "변경 이력"}
              />
            ))}
          </ol>
        </div>

        <form
          className="self-start rounded-md border border-[#e2e7e0] p-3"
          onSubmit={onAdjust}
        >
          <h3 className="text-sm font-bold">수량 보정</h3>
          <p className="mt-1 text-xs text-[#68756c]">
            판매 + 대기 + 반환 = 출하 수량
          </p>
          <div className="mt-3 grid grid-cols-3 gap-2">
            <NumberInput
              name="soldQuantity"
              label="판매"
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
          <label className="mt-3 block text-xs font-semibold">
            작업자
            <input
              name="worker"
              className="mt-1 h-8 w-full rounded border border-[#d9e0d8] px-2 text-sm"
            />
          </label>
          <label className="mt-2 block text-xs font-semibold">
            보정 사유
            <textarea
              name="memo"
              required
              className="mt-1 min-h-16 w-full rounded border border-[#d9e0d8] p-2 text-sm"
            />
          </label>
          <button
            className="mt-3 h-9 w-full rounded-md bg-[#159447] text-sm font-bold text-white disabled:opacity-50"
            type="submit"
            disabled={loading}
          >
            수량 보정 저장
          </button>
          <p className="mt-3 text-xs text-[#68756c]">
            검수: {auctionInspectionLabel(lot.inspectionStatus)} · 매출{" "}
            {lot.totalAmount.toLocaleString()}원
          </p>
        </form>
      </div>
    </section>
  );
}

function Quantity({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <p className="text-xs text-[#68756c]">{label}</p>
      <p className="mt-1 text-base font-bold">{value.toLocaleString()}분</p>
    </div>
  );
}
function TimelineItem({
  date,
  title,
  description,
}: {
  date: string;
  title: string;
  description: string;
}) {
  return (
    <li className="relative rounded-md border border-[#e5e9e3] px-3 py-2 before:absolute before:top-4 before:-left-[21px] before:h-2.5 before:w-2.5 before:rounded-full before:bg-[#159447]">
      <div className="flex flex-wrap justify-between gap-2">
        <strong className="text-sm">{title}</strong>
        <time className="text-xs text-[#68756c]">{date}</time>
      </div>
      <p className="mt-1 text-xs text-[#5c6960]">{description}</p>
    </li>
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
