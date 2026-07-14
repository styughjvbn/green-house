import { useState, type FormEvent, type ReactNode } from "react";
import { Plus, RotateCcw, SlidersHorizontal, Trash2 } from "lucide-react";
import type {
  AuctionAttemptStatus,
  AuctionInspectionStatus,
  AuctionLot,
} from "@/entities/farm/types";
import {
  auctionAttemptStatusLabel,
  auctionInspectionLabel,
} from "../../lib/auctionDisplay";
import { StatusBadge } from "./AuctionLotList";

type ResultLineForm = {
  key: string;
  auctionGrade: string;
  quantity: string;
  unitPrice: string;
  note: string;
};

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
  const [resultDate, setResultDate] = useState("");
  const [resultStatus, setResultStatus] =
    useState<AuctionAttemptStatus>("SOLD");
  const [failedReason, setFailedReason] = useState("");
  const [resultMemo, setResultMemo] = useState("");
  const [resultLines, setResultLines] = useState<ResultLineForm[]>([]);
  const [returnQuantity, setReturnQuantity] = useState(1);
  const [returnDate, setReturnDate] = useState("");

  if (!lot) {
    return (
      <section className="rounded-md border border-[#dfe5dc] bg-white p-8 text-center text-sm text-[#6c786f]">
        조회할 lot를 선택하세요.
      </section>
    );
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
  const nextAttemptNo =
    currentLot.attempts.reduce(
      (max, attempt) => Math.max(max, attempt.attemptNo),
      0,
    ) + 1;
  const isSellStatus =
    resultStatus === "SOLD" || resultStatus === "PARTIALLY_SOLD";
  const resultLineQuantity = resultLines.reduce(
    (sum, line) => sum + Number(line.quantity || 0),
    0,
  );

  function resetResultForm(nextStatus: AuctionAttemptStatus = "SOLD") {
    setResultStatus(nextStatus);
    setFailedReason("");
    setResultMemo("");
    setResultDate(currentLot.latestAuctionDate ?? currentLot.shipmentDate);
    setResultLines([
      createResultLine(currentLot.shipmentGrade, currentLot.waitingQuantity),
    ]);
  }

  function updateResultLine(
    key: string,
    field: keyof Omit<ResultLineForm, "key">,
    value: string,
  ) {
    setResultLines((current) =>
      current.map((line) =>
        line.key === key ? { ...line, [field]: value } : line,
      ),
    );
  }

  async function submitResult(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!resultDate) {
      window.alert("경매일을 입력하세요.");
      return;
    }

    if (isSellStatus) {
      const normalizedLines = resultLines
        .map((line) => ({
          auctionGrade: line.auctionGrade.trim() || null,
          quantity: Number(line.quantity),
          unitPrice: Number(line.unitPrice),
          note: line.note.trim() || null,
          inspectionStatus: "NORMAL" as const,
        }))
        .filter((line) => line.quantity > 0);

      if (normalizedLines.length === 0) {
        window.alert("낙찰 결과 행을 입력하세요.");
        return;
      }

      const totalQuantity = normalizedLines.reduce(
        (sum, line) => sum + line.quantity,
        0,
      );

      if (
        resultStatus === "SOLD" &&
        totalQuantity !== currentLot.waitingQuantity
      ) {
        window.alert("낙찰은 남은 대기 수량 전체를 입력해야 합니다.");
        return;
      }

      if (
        resultStatus === "PARTIALLY_SOLD" &&
        (totalQuantity < 1 || totalQuantity >= currentLot.waitingQuantity)
      ) {
        window.alert("부분 낙찰 수량은 대기 수량보다 적어야 합니다.");
        return;
      }

      await onAddResult({
        auctionDate: resultDate,
        attemptStatus: resultStatus,
        failedReason: failedReason.trim() || null,
        memo: resultMemo.trim() || null,
        resultLines: normalizedLines,
      });
    } else {
      await onAddResult({
        auctionDate: resultDate,
        attemptStatus: resultStatus,
        failedReason: failedReason.trim() || null,
        memo: resultMemo.trim() || null,
      });
    }

    setShowResultForm(false);
    resetResultForm();
  }

  return (
    <section className="min-w-0 rounded-md border border-[#dfe5dc] bg-white shadow-sm">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-[#e7ebe5] px-4 py-3">
        <div>
          <p className="text-xs font-semibold text-[#6b786f]">
            LOT #{currentLot.id}
          </p>
          <h2 className="text-base font-bold">
            {currentLot.varietyName} · {currentLot.auctionMarket}
          </h2>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={currentLot.currentStatus} />
          <button
            className="inline-flex h-8 items-center gap-1.5 rounded-md border border-[#d7ded5] px-3 text-xs font-semibold"
            type="button"
            onClick={() => {
              if (!showResultForm) resetResultForm();
              setShowResultForm((current) => !current);
            }}
          >
            <Plus className="h-3.5 w-3.5" />
            {showResultForm ? "입력 닫기" : "경매 결과 입력"}
          </button>
          <button
            className="inline-flex h-8 items-center gap-1.5 rounded-md border border-[#d7ded5] px-3 text-xs font-semibold"
            type="button"
            onClick={() => setShowQuantityAdjustment((current) => !current)}
          >
            <SlidersHorizontal className="h-3.5 w-3.5" />
            {showQuantityAdjustment ? "보정 닫기" : "수량 보정"}
          </button>
          {currentLot.currentStatus === "REAUCTION_WAITING" ||
          currentLot.currentStatus === "RETURN_INFERRED" ||
          currentLot.currentStatus === "PARTIALLY_RETURNED" ? (
            <button
              className="inline-flex h-8 items-center gap-1.5 rounded-md border border-[#d7ded5] px-3 text-xs font-semibold disabled:opacity-50"
              type="button"
              disabled={loading || currentLot.returnConfirmableQuantity === 0}
              onClick={() => {
                setReturnQuantity(currentLot.returnConfirmableQuantity);
                setReturnDate(
                  [...currentLot.attempts]
                    .reverse()
                    .find(
                      (attempt) => attempt.attemptStatus === "RETURN_INFERRED",
                    )?.auctionDate ??
                    currentLot.latestAuctionDate ??
                    currentLot.shipmentDate,
                );
                setShowReturnConfirmation((current) => !current);
              }}
            >
              <RotateCcw className="h-3.5 w-3.5" />
              {currentLot.currentStatus === "REAUCTION_WAITING"
                ? "반환 처리"
                : "반환 확인"}
            </button>
          ) : null}
        </div>
      </header>

      {showResultForm ? (
        <form
          className="border-b border-[#e7ebe5] bg-[#f6faf6] px-4 py-3"
          onSubmit={submitResult}
        >
          <div className="grid gap-3 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)]">
            <label className="text-xs font-semibold text-[#5b675f]">
              경매일
              <input
                className="mt-1 h-9 w-full rounded-md border border-[#d9e0d8] bg-white px-3 text-sm"
                type="date"
                required
                value={resultDate}
                onChange={(event) => setResultDate(event.target.value)}
              />
            </label>
            <label className="text-xs font-semibold text-[#5b675f]">
              결과
              <select
                className="mt-1 h-9 w-full rounded-md border border-[#d9e0d8] bg-white px-3 text-sm"
                value={resultStatus}
                onChange={(event) => {
                  const nextStatus = event.target.value as AuctionAttemptStatus;
                  setResultStatus(nextStatus);
                  if (
                    nextStatus === "SOLD" ||
                    nextStatus === "PARTIALLY_SOLD"
                  ) {
                    setResultLines((current) =>
                      current.length > 0
                        ? current
                        : [
                            createResultLine(
                              currentLot.shipmentGrade,
                              currentLot.waitingQuantity,
                            ),
                          ],
                    );
                  }
                }}
              >
                <option value="SOLD">낙찰</option>
                <option value="PARTIALLY_SOLD">부분 낙찰</option>
                <option value="FAILED">유찰</option>
                <option value="RETURN_INFERRED">반환 추정</option>
              </select>
            </label>
            <div className="text-xs font-semibold text-[#5b675f]">
              차수
              <div className="mt-1 flex h-9 items-center rounded-md border border-[#d9e0d8] bg-white px-3 text-sm font-bold text-[#233127]">
                {nextAttemptNo}차
              </div>
            </div>
            <label className="text-xs font-semibold text-[#5b675f]">
              비고
              <input
                className="mt-1 h-9 w-full rounded-md border border-[#d9e0d8] bg-white px-3 text-sm"
                value={failedReason}
                onChange={(event) => setFailedReason(event.target.value)}
                placeholder={
                  resultStatus === "FAILED" ||
                  resultStatus === "RETURN_INFERRED"
                    ? "유찰/반환 메모"
                    : "잔량 유찰 메모"
                }
              />
            </label>
          </div>

          {isSellStatus ? (
            <div className="mt-3 rounded-md border border-[#dfe6dd] bg-white p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h3 className="text-sm font-bold">낙찰 결과 행</h3>
                  <p className="mt-1 text-xs text-[#6a766e]">
                    입력 수량 {resultLineQuantity.toLocaleString()}분 / 대기{" "}
                    {currentLot.waitingQuantity.toLocaleString()}분
                  </p>
                </div>
                <button
                  className="inline-flex h-8 items-center gap-1 rounded-md border border-[#d7ded5] px-3 text-xs font-semibold"
                  type="button"
                  onClick={() =>
                    setResultLines((current) => [
                      ...current,
                      createResultLine(currentLot.shipmentGrade, 0),
                    ])
                  }
                >
                  <Plus className="h-3.5 w-3.5" />행 추가
                </button>
              </div>
              <div className="mt-3 space-y-2">
                {resultLines.map((line) => (
                  <div
                    key={line.key}
                    className="grid gap-2 rounded-md border border-[#e5e9e3] p-3 lg:grid-cols-[1fr_100px_120px_1.3fr_40px]"
                  >
                    <InputField
                      label="등급"
                      value={line.auctionGrade}
                      onChange={(value) =>
                        updateResultLine(line.key, "auctionGrade", value)
                      }
                    />
                    <InputField
                      label="수량"
                      type="number"
                      min={1}
                      value={line.quantity}
                      onChange={(value) =>
                        updateResultLine(line.key, "quantity", value)
                      }
                    />
                    <InputField
                      label="단가"
                      type="number"
                      min={1}
                      value={line.unitPrice}
                      onChange={(value) =>
                        updateResultLine(line.key, "unitPrice", value)
                      }
                    />
                    <InputField
                      label="메모"
                      value={line.note}
                      onChange={(value) =>
                        updateResultLine(line.key, "note", value)
                      }
                    />
                    <div className="flex items-end">
                      <button
                        className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-[#e1e6df] text-[#6b786f] disabled:opacity-40"
                        type="button"
                        disabled={resultLines.length === 1}
                        onClick={() =>
                          setResultLines((current) =>
                            current.filter((item) => item.key !== line.key),
                          )
                        }
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          <label className="mt-3 block text-xs font-semibold text-[#5b675f]">
            메모
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-[#d9e0d8] bg-white p-3 text-sm"
              value={resultMemo}
              onChange={(event) => setResultMemo(event.target.value)}
              placeholder="운영 메모"
            />
          </label>

          <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs text-[#6a766e]">
              {resultStatus === "SOLD"
                ? "낙찰은 대기 수량 전체 입력"
                : resultStatus === "PARTIALLY_SOLD"
                  ? "부분 낙찰은 잔량이 자동으로 유찰 처리"
                  : resultStatus === "FAILED"
                    ? "대기 수량 전체를 유찰로 기록"
                    : "대기 수량 전체를 반환 추정으로 기록"}
            </p>
            <button
              className="h-9 rounded-md bg-[#159447] px-4 text-sm font-bold text-white disabled:opacity-50"
              type="submit"
              disabled={loading || !resultDate}
            >
              경매 결과 저장
            </button>
          </div>
        </form>
      ) : null}

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
              확인 가능 {currentLot.returnConfirmableQuantity.toLocaleString()}
              분
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

      <div
        className={`grid gap-3 p-4 ${showQuantityAdjustment ? "lg:grid-cols-[minmax(0,1fr)_300px]" : ""}`}
      >
        <div>
          <div className="grid grid-cols-2 gap-2 rounded-md bg-[#f7f9f6] p-3 text-center sm:grid-cols-4">
            <Quantity label="출하" value={currentLot.shippedQuantity} />
            <Quantity label="낙찰" value={currentLot.soldQuantity} />
            <Quantity label="대기" value={currentLot.waitingQuantity} />
            <Quantity label="반환" value={currentLot.returnedQuantity} />
          </div>
          <h3 className="mt-4 mb-2 text-sm font-bold">경매 진행 타임라인</h3>
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

        {showQuantityAdjustment ? (
          <form
            className="self-start rounded-md border border-[#e2e7e0] p-3"
            onSubmit={onAdjust}
          >
            <h3 className="text-sm font-bold">수량 보정</h3>
            <p className="mt-1 text-xs text-[#68756c]">
              낙찰 + 대기 + 반환 = 출하 수량
            </p>
            <div className="mt-3 grid grid-cols-3 gap-2">
              <NumberInput
                name="soldQuantity"
                label="낙찰"
                value={currentLot.soldQuantity}
              />
              <NumberInput
                name="waitingQuantity"
                label="대기"
                value={currentLot.waitingQuantity}
              />
              <NumberInput
                name="returnedQuantity"
                label="반환"
                value={currentLot.returnedQuantity}
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
              검수 {auctionInspectionLabel(currentLot.inspectionStatus)} · 매출{" "}
              {currentLot.totalAmount.toLocaleString()}원
            </p>
          </form>
        ) : null}
      </div>
    </section>
  );
}

function createResultLine(
  grade: string | null,
  quantity: number,
): ResultLineForm {
  return {
    key: `${Date.now()}-${Math.random()}`,
    auctionGrade: grade ?? "",
    quantity: quantity > 0 ? String(quantity) : "",
    unitPrice: "",
    note: "",
  };
}

function Quantity({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <p className="text-xs text-[#68756c]">{label}</p>
      <p className="mt-1 text-base font-bold">{value.toLocaleString()}분</p>
    </div>
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
        <time className="text-xs text-[#68756c]">{date}</time>
      </div>
      <div className="mt-1 text-xs text-[#5c6960]">{description}</div>
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

function InputField({
  label,
  value,
  onChange,
  type = "text",
  min,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: "text" | "number";
  min?: number;
}) {
  return (
    <label className="text-xs font-semibold text-[#5b675f]">
      {label}
      <input
        className="mt-1 h-9 w-full rounded-md border border-[#d9e0d8] bg-white px-3 text-sm"
        type={type}
        min={min}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
