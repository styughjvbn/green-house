import { useState, type FormEvent } from "react";
import { Plus, Trash2, X } from "lucide-react";
import type {
  AuctionAttemptStatus,
  AuctionInspectionStatus,
  AuctionLot,
} from "@/entities/farm/types";

type ResultLineForm = {
  key: string;
  auctionGrade: string;
  quantity: string;
  unitPrice: string;
  note: string;
};

type AuctionResultPayload = {
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
};

export function AuctionResultDialog({
  lot,
  loading,
  onClose,
  onSubmit,
}: {
  lot: AuctionLot;
  loading: boolean;
  onClose: () => void;
  onSubmit: (payload: AuctionResultPayload) => Promise<void>;
}) {
  const [resultDate, setResultDate] = useState(
    lot.latestAuctionDate ?? lot.shipmentDate,
  );
  const [resultStatus, setResultStatus] =
    useState<AuctionAttemptStatus>("SOLD");
  const [failedReason, setFailedReason] = useState("");
  const [resultMemo, setResultMemo] = useState("");
  const [resultLines, setResultLines] = useState<ResultLineForm[]>([
    createResultLine(lot.shipmentGrade, lot.waitingQuantity),
  ]);
  const nextAttemptNo =
    lot.attempts.reduce((max, attempt) => Math.max(max, attempt.attemptNo), 0) +
    1;
  const isSellStatus =
    resultStatus === "SOLD" || resultStatus === "PARTIALLY_SOLD";
  const resultLineQuantity = resultLines.reduce(
    (sum, line) => sum + Number(line.quantity || 0),
    0,
  );

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

      if (resultStatus === "SOLD" && totalQuantity !== lot.waitingQuantity) {
        window.alert("낙찰은 남은 대기 수량 전체를 입력해야 합니다.");
        return;
      }

      if (
        resultStatus === "PARTIALLY_SOLD" &&
        (totalQuantity < 1 || totalQuantity >= lot.waitingQuantity)
      ) {
        window.alert("부분 낙찰 수량은 대기 수량보다 적어야 합니다.");
        return;
      }

      await onSubmit({
        auctionDate: resultDate,
        attemptStatus: resultStatus,
        failedReason: failedReason.trim() || null,
        memo: resultMemo.trim() || null,
        resultLines: normalizedLines,
      });
    } else {
      await onSubmit({
        auctionDate: resultDate,
        attemptStatus: resultStatus,
        failedReason: failedReason.trim() || null,
        memo: resultMemo.trim() || null,
      });
    }

    onClose();
  }

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 p-4"
      role="presentation"
      onMouseDown={onClose}
    >
      <section
        className="flex max-h-[calc(100dvh-2rem)] w-full max-w-4xl flex-col rounded-md bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="경매 결과 입력"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex shrink-0 items-start justify-between gap-3 border-b border-[#edf0ec] p-5">
          <div>
            <p className="text-sm font-semibold text-[#3d6f91]">
              LOT #{lot.id}
            </p>
            <h2 className="mt-1 text-xl font-semibold">경매 결과 입력</h2>
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

        <form
          className="min-h-0 space-y-3 overflow-y-auto p-5"
          onSubmit={submitResult}
        >
          <div className="grid gap-3 xl:grid-cols-4">
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
                              lot.shipmentGrade,
                              lot.waitingQuantity,
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
            <div className="rounded-md border border-[#dfe6dd] bg-white p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h3 className="text-sm font-bold">낙찰 결과 행</h3>
                  <p className="mt-1 text-xs text-[#6a766e]">
                    입력 수량 {resultLineQuantity.toLocaleString()}분 / 대기{" "}
                    {lot.waitingQuantity.toLocaleString()}분
                  </p>
                </div>
                <button
                  className="inline-flex h-8 items-center gap-1 rounded-md border border-[#d7ded5] px-3 text-xs font-semibold"
                  type="button"
                  onClick={() =>
                    setResultLines((current) => [
                      ...current,
                      createResultLine(lot.shipmentGrade, 0),
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

          <label className="block text-xs font-semibold text-[#5b675f]">
            메모
            <textarea
              className="mt-1 min-h-20 w-full rounded-md border border-[#d9e0d8] bg-white p-3 text-sm"
              value={resultMemo}
              onChange={(event) => setResultMemo(event.target.value)}
              placeholder="운영 메모"
            />
          </label>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs text-[#6a766e]">
              {resultStatus === "SOLD"
                ? "낙찰은 대기 수량 전체 입력"
                : resultStatus === "PARTIALLY_SOLD"
                  ? "부분 낙찰은 잔량이 자동으로 유찰 처리"
                  : resultStatus === "FAILED"
                    ? "대기 수량 전체를 유찰로 기록"
                    : "대기 수량 전체를 반환 추정으로 기록"}
            </p>
            <div className="flex gap-2">
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
                disabled={loading || !resultDate}
              >
                경매 결과 저장
              </button>
            </div>
          </div>
        </form>
      </section>
    </div>
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
