import { useState, type FormEvent, type ReactNode } from "react";
import { Plus, RotateCcw, SlidersHorizontal } from "lucide-react";
import type {
  AuctionAttemptStatus,
  AuctionInspectionStatus,
  AuctionLot,
} from "@/entities/farm/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import { auctionAttemptStatusLabel } from "../../lib/auctionDisplay";
import {
  DetailCard,
  DetailActionButton,
  DetailEmpty,
  DetailHeader,
  DetailSummary,
} from "@/shared/ui/DetailCard";
import { AuctionLotStatusBadge } from "@/features/sales/ui/common/SalesStatusBadge";
import { AuctionQuantityAdjustDialog } from "./AuctionQuantityAdjustDialog";
import { AuctionResultDialog } from "./AuctionResultDialog";

export function AuctionLotDetail({
  lot,
  loading,
  onAddResult,
  onConfirmReturn,
  onAdjust,
}: {
  lot: AuctionLot | null;
  loading: boolean;
  onAddResult: (payload: {
    auctionDate: string;
    attemptStatus: AuctionAttemptStatus;
    failedReason: string | null;
    memo: string | null;
    resultLines?: Array<{
      auctionGrade: string | null;
      quantity: number;
      unitPrice: number;
      note: string | null;
      inspectionStatus: AuctionInspectionStatus | null;
    }>;
  }) => Promise<void>;
  onConfirmReturn: (
    returnedQuantity: number,
    returnDate: string,
  ) => Promise<void>;
  onAdjust: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const [showResultForm, setShowResultForm] = useState(false);
  const [showReturnConfirmation, setShowReturnConfirmation] = useState(false);
  const [showQuantityAdjustment, setShowQuantityAdjustment] = useState(false);
  const [returnQuantity, setReturnQuantity] = useState(1);
  const [returnDate, setReturnDate] = useState("");

  if (!lot) {
    return <DetailEmpty>조회할 lot를 선택하세요.</DetailEmpty>;
  }

  const currentLot = lot;
  const soldResultLines = currentLot.attempts
    .flatMap((attempt) => attempt.resultLines)
    .filter((line) => line.amount > 0);
  const soldAmount = soldResultLines.reduce(
    (sum, line) => sum + line.amount,
    0,
  );
  const soldQuantity = soldResultLines.reduce(
    (sum, line) => sum + line.quantity,
    0,
  );
  const averageUnitPrice =
    soldQuantity > 0 ? Math.round(soldAmount / soldQuantity) : 0;

  return (
    <>
      <DetailCard>
        <DetailHeader
          eyebrow={`LOT #${currentLot.id}`}
          eyebrowAside={
            <AuctionLotStatusBadge
              size="compact"
              status={currentLot.currentStatus}
            />
          }
          title={`${currentLot.varietyName} · ${currentLot.auctionMarket}`}
          summary={
            <DetailSummary
              align="left"
              items={[
                {
                  label: "출하",
                  value: `${currentLot.shippedQuantity.toLocaleString()}분`,
                },
                {
                  label: "낙찰",
                  value: `${currentLot.soldQuantity.toLocaleString()}분`,
                },
                {
                  label: "대기",
                  value: `${currentLot.waitingQuantity.toLocaleString()}분`,
                },
                {
                  label: "반환",
                  value: `${currentLot.returnedQuantity.toLocaleString()}분`,
                },
              ]}
            />
          }
          actions={
            <>
              <DetailActionButton
                icon={Plus}
                onClick={() => setShowResultForm(true)}
              >
                경매 결과 입력
              </DetailActionButton>
              <DetailActionButton
                icon={SlidersHorizontal}
                onClick={() => setShowQuantityAdjustment(true)}
              >
                수량 보정
              </DetailActionButton>
              {currentLot.currentStatus === "REAUCTION_WAITING" ||
              currentLot.currentStatus === "RETURN_INFERRED" ||
              currentLot.currentStatus === "PARTIALLY_RETURNED" ? (
                <DetailActionButton
                  disabled={
                    loading || currentLot.returnConfirmableQuantity === 0
                  }
                  icon={RotateCcw}
                  onClick={() => {
                    setReturnQuantity(currentLot.returnConfirmableQuantity);
                    setReturnDate(
                      [...currentLot.attempts]
                        .reverse()
                        .find(
                          (attempt) =>
                            attempt.attemptStatus === "RETURN_INFERRED",
                        )?.auctionDate ??
                        currentLot.latestAuctionDate ??
                        currentLot.shipmentDate,
                    );
                    setShowReturnConfirmation((current) => !current);
                  }}
                >
                  {currentLot.currentStatus === "REAUCTION_WAITING"
                    ? "반환 처리"
                    : "반환 확인"}
                </DetailActionButton>
              ) : null}
            </>
          }
        />

        {showReturnConfirmation ? (
          <form
            className="flex flex-wrap items-end gap-3 border-b border-[#e7ebe5] bg-[#fff9ed] px-4 py-3"
            onSubmit={async (event) => {
              event.preventDefault();
              await onConfirmReturn(returnQuantity, returnDate);
              setShowReturnConfirmation(false);
            }}
          >
            <label className="text-xs font-semibold text-[#5a4932]">
              반환 확인 수량
              <input
                className="mt-1 block h-9 w-36 rounded-md border border-[#d8c7a8] bg-white px-3 text-right text-sm"
                type="number"
                min={1}
                max={currentLot.returnConfirmableQuantity}
                required
                value={returnQuantity}
                onChange={(event) =>
                  setReturnQuantity(Number(event.target.value))
                }
              />
            </label>
            <label className="text-xs font-semibold text-[#5a4932]">
              반환 날짜
              <input
                className="mt-1 block h-9 w-40 rounded-md border border-[#d8c7a8] bg-white px-3 text-sm"
                type="date"
                required
                value={returnDate}
                onChange={(event) => setReturnDate(event.target.value)}
              />
            </label>
            <div className="min-w-40 text-xs text-[#6b5b44]">
              <p>
                확인 가능{" "}
                {currentLot.returnConfirmableQuantity.toLocaleString()}분
              </p>
              <p className="mt-1 font-bold">
                변경 상태:{" "}
                {returnQuantity === currentLot.returnConfirmableQuantity
                  ? "반환완료"
                  : "부분반환"}
              </p>
            </div>
            <button
              className="h-9 rounded-md bg-[#159447] px-4 text-sm font-bold text-white disabled:opacity-50"
              type="submit"
              disabled={
                loading ||
                returnQuantity < 1 ||
                returnQuantity > currentLot.returnConfirmableQuantity ||
                !returnDate
              }
            >
              반환 확인 저장
            </button>
          </form>
        ) : null}

        <div className="grid gap-3 p-4">
          <div>
            <h3 className="mb-2 text-sm font-bold">경매 진행 타임라인</h3>
            <ol className="space-y-2 border-l-2 border-[#dce9da] pl-4">
              <TimelineItem
                date={currentLot.shipmentDate}
                title={`경매장 출하 ${currentLot.shippedQuantity.toLocaleString()}분`}
                description={`${currentLot.boxes == null ? "상자 수 미지정" : `${currentLot.boxes.toLocaleString()}상자`} · 출하등급 ${currentLot.shipmentGrade || "미지정"}`}
              />
              {currentLot.attempts.map((attempt) => {
                const attemptSoldQuantity = attempt.resultLines
                  .filter((line) => line.amount > 0)
                  .reduce((sum, line) => sum + line.quantity, 0);
                const attemptTitle =
                  attempt.attemptStatus === "RETURN_INFERRED"
                    ? "반환 추정"
                    : `${attempt.attemptNo}차 경매 · ${auctionAttemptStatusLabel(attempt.attemptStatus)}`;

                return (
                  <TimelineItem
                    key={attempt.id}
                    date={attempt.auctionDate}
                    alert={attempt.attemptStatus === "RETURN_INFERRED"}
                    title={
                      <span className="inline-flex items-center gap-2">
                        <span>{attemptTitle}</span>
                        {attemptSoldQuantity > 0 ? (
                          <span className="text-[11px] font-medium text-[#159447]">
                            {attemptSoldQuantity.toLocaleString()}분 낙찰
                          </span>
                        ) : null}
                      </span>
                    }
                    description={
                      attempt.resultLines.length > 0 ? (
                        <ul className="space-y-1">
                          {attempt.resultLines.map((line) => (
                            <li key={line.id} className="flex gap-2">
                              <span className="text-[#98a29a]">•</span>
                              <span>
                                {line.quantity.toLocaleString()}분 ·{" "}
                                {line.amount > 0 ? (
                                  <>
                                    단가 {line.unitPrice.toLocaleString()}원 ·
                                    총액 {line.amount.toLocaleString()}원
                                  </>
                                ) : attempt.attemptStatus ===
                                  "RETURN_INFERRED" ? (
                                  "반환"
                                ) : (
                                  "유찰"
                                )}
                              </span>
                            </li>
                          ))}
                        </ul>
                      ) : (
                        attempt.failedReason || "결과 없음"
                      )
                    }
                  />
                );
              })}
              {currentLot.statusHistory.map((history) => (
                <TimelineItem
                  key={`history-${history.id}`}
                  date={history.changedAt.slice(0, 10)}
                  title={`상태 변경 · ${history.reason}`}
                  description={history.memo || history.worker || "변경 이력"}
                />
              ))}
            </ol>
            <div className="mt-3 flex justify-end gap-8 border-t border-[#e5e9e3] pt-3">
              <Money label="낙찰금액" value={soldAmount} />
              <Money label="평균 단가" value={averageUnitPrice} />
            </div>
          </div>
        </div>
      </DetailCard>

      {showResultForm ? (
        <AuctionResultDialog
          key={currentLot.id}
          lot={currentLot}
          loading={loading}
          onClose={() => setShowResultForm(false)}
          onSubmit={onAddResult}
        />
      ) : null}

      {showQuantityAdjustment ? (
        <AuctionQuantityAdjustDialog
          key={currentLot.id}
          lot={currentLot}
          loading={loading}
          onClose={() => setShowQuantityAdjustment(false)}
          onSubmit={onAdjust}
        />
      ) : null}
    </>
  );
}

function Money({ label, value }: { label: string; value: number }) {
  return (
    <div className="min-w-24 text-right">
      <p className="text-xs text-[#68756c]">{label}</p>
      <p className="mt-1 text-base font-bold">{value.toLocaleString()}원</p>
    </div>
  );
}

function TimelineItem({
  date,
  title,
  description,
  alert = false,
}: {
  date: string;
  title: ReactNode;
  description: ReactNode;
  alert?: boolean;
}) {
  return (
    <li
      className={`relative rounded-md border border-[#e5e9e3] px-3 py-2 before:absolute before:top-4 before:-left-[23px] before:h-2.5 before:w-2.5 before:rounded-full ${alert ? "before:bg-[#dc2626]" : "before:bg-[#159447]"}`}
    >
      <div className="flex flex-wrap justify-between gap-2">
        <strong className="text-sm">{title}</strong>
        <time className="text-xs text-[#68756c]">{formatShortDate(date)}</time>
      </div>
      <div className="mt-1 text-xs text-[#5c6960]">{description}</div>
    </li>
  );
}
