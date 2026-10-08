"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import type { AuctionLot } from "@/entities/farm/types";
import type { FarmPlacementSelection } from "@/entities/farm/model/placement";
import { FarmPlacementField } from "@/entities/farm/ui/FarmPlacementPicker";
import {
  PlacementTypeField,
  PotSizeField,
} from "@/entities/farm/ui/PottingExecutionForm";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/shared/ui/primitives/dialog";
import type {
  AuctionArrival,
  AuctionFollowUp,
  AuctionFollowUpMethod,
} from "../../api/types";
import {
  getAuctionReturnHouses,
  getAuctionReturnVarieties,
} from "../../api/salesApi";
import type { AuctionFollowUpInput } from "../../lib/auctionFollowUpRequest";
import { buildAuctionArrivalInput } from "../../lib/auctionArrivalPayload";
import { followUpLabels } from "../../lib/auctionDisplay";

const inputClass =
  "mt-1 h-10 w-full rounded-md border border-[#d7ddd8] bg-white px-3 text-sm";
export function AuctionFollowUpDialog({
  mode,
  lot,
  summary,
  saving,
  blocked,
  onClose,
  onReturnFocus,
  onSubmit,
}: {
  mode: "decision" | "arrival" | AuctionArrival;
  lot: AuctionLot;
  summary: AuctionFollowUp;
  saving: boolean;
  blocked: boolean;
  onClose: () => void;
  onReturnFocus: () => void;
  onSubmit: (input: AuctionFollowUpInput) => Promise<void>;
}) {
  const { businessDate } = useRuntimeContext();
  const [method, setMethod] = useState<AuctionFollowUpMethod | "">("");
  const [date, setDate] = useState(businessDate);
  const [quantity, setQuantity] = useState(String(summary.pendingQuantity));
  const [varietyId, setVarietyId] = useState("");
  const [potSize, setPotSize] = useState("");
  const [ageYear, setAgeYear] = useState("");
  const [status, setStatus] = useState("정상");
  const [placementType, setPlacementType] = useState("");
  const [trayCount, setTrayCount] = useState("");
  const [placement, setPlacement] = useState<FarmPlacementSelection | null>(
    null,
  );
  const [reason, setReason] = useState("");
  const [worker, setWorker] = useState("");
  const [memo, setMemo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const houses = useQuery({
    queryKey: ["sales", "auctionReturnHouses"],
    queryFn: ({ signal }) => getAuctionReturnHouses(signal),
    enabled: mode === "arrival",
  });
  const varieties = useQuery({
    queryKey: ["sales", "auctionReturnVarieties"],
    queryFn: ({ signal }) => getAuctionReturnVarieties(signal),
    enabled: mode === "arrival",
  });
  const cancellation = typeof mode === "object" ? mode : null;
  const title =
    mode === "decision"
      ? "유찰 잔량 처리 방법 선택"
      : mode === "arrival"
        ? "실제 반환품 도착 기록"
        : "도착 취소 정정";
  const disabled =
    blocked ||
    (mode === "arrival" &&
      (houses.isPending ||
        varieties.isPending ||
        houses.isError ||
        varieties.isError));
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    try {
      let input: AuctionFollowUpInput;
      if (mode === "decision") {
        if (!method || !summary.availableMethods.includes(method))
          throw new Error("처리 방법을 선택하세요.");
        input = {
          operation: "FOLLOW_UP",
          payload: {
            method,
            reason: reason.trim(),
            ...(worker.trim() ? { worker: worker.trim() } : {}),
          },
        };
      } else if (mode === "arrival") {
        input = buildAuctionArrivalInput({
          arrivalDate: date,
          varietyId,
          quantity,
          potSize,
          ageYear,
          status,
          placementType,
          trayCount,
          placement,
          memo,
          worker,
        });
      } else {
        input = {
          operation: "ARRIVAL_CANCEL",
          arrivalId: mode.id,
          payload: {
            correctionDate: date,
            reason: reason.trim(),
            ...(worker.trim() ? { worker: worker.trim() } : {}),
          },
        };
      }
      await onSubmit(input);
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : "저장하지 못했습니다.",
      );
    }
  }
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !saving) onClose();
      }}
    >
      <DialogContent
        className="max-h-[90dvh] max-w-2xl overflow-y-auto rounded-lg p-5"
        showCloseButton={false}
        onCloseAutoFocus={(event) => {
          event.preventDefault();
          onReturnFocus();
        }}
      >
        <DialogTitle className="pr-10 text-lg font-bold">{title}</DialogTitle>
        <DialogDescription className="mt-2 text-sm text-[#68756c]">
          LOT #{lot.id} · {lot.varietyName}
        </DialogDescription>
        <form className="mt-4 space-y-4" onSubmit={submit}>
          <fieldset
            disabled={disabled}
            className="space-y-4 disabled:opacity-60"
          >
            {mode === "decision" ? (
              <>
                <p className="text-sm">
                  유찰 잔량 전체에 한 가지 방법을 선택합니다.
                </p>
                <div className="space-y-2">
                  {summary.availableMethods.map((choice) => (
                    <label
                      key={choice}
                      className="flex cursor-pointer gap-3 rounded border bg-white p-3 text-sm font-semibold"
                    >
                      <input
                        type="radio"
                        name="followUpMethod"
                        required
                        value={choice}
                        checked={method === choice}
                        onChange={() => setMethod(choice)}
                      />
                      {followUpLabels[choice]}
                    </label>
                  ))}
                </div>
                {method === "FARM_RETURN" ? (
                  <p className="text-sm">
                    이 결정은 농장 재고를 만들지 않습니다. 도착한 뒤 수량과
                    배치를 기록하세요.
                  </p>
                ) : null}
                {method === "AUCTION_DISPOSAL" ? (
                  <p className="text-sm">
                    저장하면 잔량 전체의 경매장 처리가 완료됩니다. 농장 재고는
                    바뀌지 않습니다.
                  </p>
                ) : null}
              </>
            ) : null}
            {mode === "arrival" ? (
              <>
                <p className="text-sm">
                  도착 대기 {summary.pendingQuantity.toLocaleString()}분 중 실제
                  도착한 수량을 기록합니다. 저장하면 선택한 구역에 별도 난
                  묶음이 생성됩니다.
                </p>
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field label="실제 도착일">
                    <input
                      className={inputClass}
                      type="date"
                      required
                      value={date}
                      onChange={(e) => setDate(e.target.value)}
                    />
                  </Field>
                  <Field label="도착 수량">
                    <input
                      className={inputClass}
                      type="number"
                      required
                      min={1}
                      max={summary.pendingQuantity}
                      step={1}
                      value={quantity}
                      onChange={(e) => setQuantity(e.target.value)}
                    />
                  </Field>
                  <Field label="반환품 품종">
                    <select
                      className={inputClass}
                      required
                      value={varietyId}
                      onChange={(e) => setVarietyId(e.target.value)}
                    >
                      <option value="">확인한 품종 선택</option>
                      {varieties.data?.varieties.map((variety) => (
                        <option key={variety.id} value={variety.id}>
                          {variety.genus} · {variety.name}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field label="반환품 상태">
                    <select
                      className={inputClass}
                      value={status}
                      onChange={(e) => setStatus(e.target.value)}
                    >
                      {["정상", "주의", "이상"].map((value) => (
                        <option key={value}>{value}</option>
                      ))}
                    </select>
                  </Field>
                  <PotSizeField value={potSize} onChange={setPotSize} />
                  <Field label="년생">
                    <input
                      className={inputClass}
                      type="number"
                      min={0}
                      step={1}
                      value={ageYear}
                      onChange={(e) => setAgeYear(e.target.value)}
                    />
                  </Field>
                  <PlacementTypeField
                    value={placementType}
                    onChange={setPlacementType}
                  />
                  <Field label="트레이 수 (선택)">
                    <input
                      className={inputClass}
                      type="number"
                      min={0}
                      step={1}
                      value={trayCount}
                      onChange={(e) => setTrayCount(e.target.value)}
                    />
                  </Field>
                </div>
                {houses.data ? (
                  <FarmPlacementField
                    houses={houses.data}
                    value={placement}
                    onChange={setPlacement}
                    dialogTitle="반환품 배치 칸 선택"
                  />
                ) : null}
                <Field label="반환품 메모 (선택)">
                  <input
                    className={inputClass}
                    value={memo}
                    onChange={(e) => setMemo(e.target.value)}
                  />
                </Field>
              </>
            ) : null}
            {cancellation ? (
              <>
                <p className="text-sm">
                  도착 기록 #{cancellation.id} ·{" "}
                  {cancellation.quantity.toLocaleString()}분과 생성 난 묶음 #
                  {cancellation.orchidGroupId}의 재고 효과를 함께 취소합니다.
                  후속 사용이 있으면 먼저 해당 사용을 정정해야 합니다.
                </p>
                <Field label="정정일">
                  <input
                    className={inputClass}
                    type="date"
                    required
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                  />
                </Field>
              </>
            ) : null}
            {mode !== "arrival" ? (
              <Field
                label={mode === "decision" ? "결정 사유" : "도착 취소 사유"}
              >
                <textarea
                  className={`${inputClass} h-20 py-2`}
                  required
                  maxLength={1000}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </Field>
            ) : null}
            <Field label="담당자 (선택)">
              <input
                className={inputClass}
                maxLength={100}
                value={worker}
                onChange={(e) => setWorker(e.target.value)}
              />
            </Field>
          </fieldset>
          {mode === "arrival" && (houses.isPending || varieties.isPending) ? (
            <p role="status">품종과 농장 배치를 불러오는 중입니다.</p>
          ) : null}
          {mode === "arrival" && (houses.error || varieties.error) ? (
            <p role="alert" className="text-sm text-red-700">
              품종·배치를 조회하지 못했습니다.{" "}
              <button
                type="button"
                onClick={() => {
                  void houses.refetch();
                  void varieties.refetch();
                }}
              >
                다시 조회
              </button>
            </p>
          ) : null}
          {error ? (
            <p
              role="alert"
              className="text-sm whitespace-pre-line text-red-700"
            >
              {error}
            </p>
          ) : null}
          <div className="flex justify-end gap-2">
            <button
              type="button"
              disabled={saving}
              className="rounded border px-4 py-2 disabled:opacity-50"
              onClick={onClose}
            >
              닫기
            </button>
            <button
              type="submit"
              disabled={disabled}
              className="rounded bg-[#159447] px-4 py-2 font-bold text-white disabled:opacity-50"
            >
              {saving
                ? "저장 중…"
                : mode === "decision"
                  ? "처리 방법 저장"
                  : mode === "arrival"
                    ? "실제 도착 저장"
                    : "도착 취소 저장"}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block text-xs font-semibold text-[#425047]">
      {label}
      {children}
    </label>
  );
}
