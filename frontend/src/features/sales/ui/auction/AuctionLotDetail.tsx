import { useState, type FormEvent, type ReactNode } from "react";
import { Plus, SlidersHorizontal } from "lucide-react";
import type { AuctionLot } from "@/entities/farm/types";
import type { AuctionResultFormPayload } from "../../api/types";
import { formatShortDate } from "@/shared/lib/dateFormat";
import {
  auctionAttemptStatusLabel,
  followUpLabels,
} from "../../lib/auctionDisplay";
import {
  DetailCard,
  DetailActionButton,
  DetailEmpty,
  DetailHeader,
  DetailSummary,
} from "@/shared/ui/DetailCard";
import { AuctionLotStatusBadge } from "@/features/sales/ui/common/SalesStatusBadge";
import { AuctionQuantityAdjustDialog } from "./AuctionQuantityAdjustDialog";
import { AuctionFollowUpPanel } from "./AuctionFollowUpPanel";
import { AuctionResultDialog } from "./AuctionResultDialog";

export function AuctionLotDetail({
  lot,
  loading,
  onAddResult,
  arrivalPage,
  onAdjust,
}: {
  lot: AuctionLot | null;
  loading: boolean;
  onAddResult: (payload: AuctionResultFormPayload) => Promise<void>;
  arrivalPage: number;
  onAdjust: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const [showResultForm, setShowResultForm] = useState(false);
  const [showQuantityAdjustment, setShowQuantityAdjustment] = useState(false);

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
                disabled={
                  loading || currentLot.quantityAdjustmentAllowed !== true
                }
                onClick={() => setShowQuantityAdjustment(true)}
              >
                수량 보정
              </DetailActionButton>
            </>
          }
        />

        <AuctionFollowUpPanel lot={currentLot} arrivalPage={arrivalPage}>
          {({ openDecision, openReturn, decisionBlocked, selectedMethod }) => (
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
                        actions={
                          currentLot.followUpAttemptId === attempt.id ? (
                            <>
                              {selectedMethod === "FARM_RETURN" ? (
                                <button
                                  type="button"
                                  className="rounded-md border border-[#b8d8be] bg-[#eef7ec] px-3 py-1 text-xs font-semibold text-[#246b38]"
                                  onClick={openReturn}
                                >
                                  농장 반환
                                </button>
                              ) : selectedMethod ? (
                                <span className="text-xs font-semibold text-[#5c6960]">
                                  {followUpLabels[selectedMethod]}
                                </span>
                              ) : null}
                              <button
                                type="button"
                                className="rounded-md border bg-white px-3 py-1 text-xs font-semibold disabled:opacity-50"
                                disabled={decisionBlocked}
                                onClick={openDecision}
                              >
                                유찰 처리
                              </button>
                            </>
                          ) : null
                        }
                        description={
                          <>
                            {attempt.resultLines.length > 0 ? (
                              <ul className="space-y-1">
                                {attempt.resultLines.map((line) => (
                                  <li key={line.id} className="flex gap-2">
                                    <span className="text-[#98a29a]">•</span>
                                    <span>
                                      {line.quantity.toLocaleString()}분 ·{" "}
                                      {line.amount > 0 ? (
                                        <>
                                          단가 {line.unitPrice.toLocaleString()}
                                          원 · 총액{" "}
                                          {line.amount.toLocaleString()}원
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
                            )}
                          </>
                        }
                      />
                    );
                  })}
                  {/* TODO: 내부 상태·수량 변경 이력의 표시 방식은 보류. 저장된 이력은 유지한다. */}
                </ol>
                <div className="mt-3 flex justify-end gap-8 border-t border-[#e5e9e3] pt-3">
                  <Money label="낙찰금액" value={soldAmount} />
                  <Money label="평균 단가" value={averageUnitPrice} />
                </div>
              </div>
            </div>
          )}
        </AuctionFollowUpPanel>
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

      {showQuantityAdjustment &&
      currentLot.quantityAdjustmentAllowed === true ? (
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
  actions,
}: {
  date: string;
  title: ReactNode;
  description: ReactNode;
  alert?: boolean;
  actions?: ReactNode;
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
      {actions ? (
        <div className="mt-2 flex items-center justify-end gap-2">
          {actions}
        </div>
      ) : null}
    </li>
  );
}
