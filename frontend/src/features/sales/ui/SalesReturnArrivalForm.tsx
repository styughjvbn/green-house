"use client";
import { useState, type FormEvent, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import type { FarmPlacementSelection } from "@/entities/farm/model/placement";
import { FarmPlacementField } from "@/entities/farm/ui/FarmPlacementPicker";
import {
  PlacementTypeField,
  PotSizeField,
} from "@/entities/farm/ui/PottingExecutionForm";
import { useRuntimeContext } from "@/shared/runtime/RuntimeContext";
import {
  getAuctionReturnHouses,
  getAuctionReturnVarieties,
} from "../api/salesApi";
import { buildAuctionArrivalInput } from "../lib/auctionArrivalPayload";
import type { DirectReturnArrivalPayload } from "../api/directChangeTypes";
const inputClass =
  "mt-1 h-10 w-full rounded-md border border-[#d7ddd8] bg-white px-3 text-sm";
export function SalesReturnArrivalForm({
  pendingQuantity,
  saving,
  blocked,
  onClose,
  onSubmit,
}: {
  pendingQuantity: number;
  saving: boolean;
  blocked: boolean;
  onClose: () => void;
  onSubmit: (payload: DirectReturnArrivalPayload) => Promise<void>;
}) {
  const { businessDate } = useRuntimeContext();
  const [date, setDate] = useState(businessDate);
  const [quantity, setQuantity] = useState(String(pendingQuantity));
  const [recordOnly, setRecordOnly] = useState(false);
  const [varietyId, setVarietyId] = useState("");
  const [potSize, setPotSize] = useState("");
  const [ageYear, setAgeYear] = useState("");
  const [status, setStatus] = useState("정상");
  const [placementType, setPlacementType] = useState("");
  const [trayCount, setTrayCount] = useState("");
  const [placement, setPlacement] = useState<FarmPlacementSelection | null>(
    null,
  );
  const [worker, setWorker] = useState("");
  const [memo, setMemo] = useState("");
  const [error, setError] = useState<string | null>(null);
  const houses = useQuery({
    queryKey: ["sales", "auctionReturnHouses"],
    queryFn: ({ signal }) => getAuctionReturnHouses(signal),
    enabled: !recordOnly,
  });
  const varieties = useQuery({
    queryKey: ["sales", "auctionReturnVarieties"],
    queryFn: ({ signal }) => getAuctionReturnVarieties(signal),
    enabled: !recordOnly,
  });
  const disabled =
    blocked ||
    saving ||
    (!recordOnly &&
      (houses.isPending ||
        varieties.isPending ||
        houses.isError ||
        varieties.isError));
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled) return;
    setError(null);
    try {
      const input = buildAuctionArrivalInput({
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
        recordOnly,
      });
      if (input.operation !== "ARRIVAL") return;
      await onSubmit({
        ...input.payload,
        quantity: Number(quantity),
        recordOnly,
      });
    } catch (failure) {
      setError(
        failure instanceof Error ? failure.message : "저장하지 못했습니다.",
      );
    }
  }
  return (
    <form className="mt-4 space-y-4" onSubmit={(event) => void submit(event)}>
      <fieldset disabled={saving || blocked} className="space-y-3">
        <legend className="text-sm font-semibold">재고 반영 방법</legend>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name="inventoryEffect"
            checked={!recordOnly}
            onChange={() => setRecordOnly(false)}
          />
          새 난 묶음 생성
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            name="inventoryEffect"
            checked={recordOnly}
            onChange={() => setRecordOnly(true)}
          />
          이미 재고에 반영됨 · 기록만 등록
        </label>
        <p className="text-xs text-[#68756c]">
          {recordOnly
            ? "도착 이력만 남기며 농장 재고를 변경하지 않습니다."
            : "선택한 구역에 새 난 묶음을 생성합니다."}
        </p>
      </fieldset>
      <fieldset disabled={disabled} className="space-y-4 disabled:opacity-60">
        <p className="text-sm">
          도착 대기 {pendingQuantity.toLocaleString()}분 중 실제 도착한 수량을
          기록합니다.
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
              max={pendingQuantity}
              step={1}
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </Field>
          {!recordOnly ? (
            <>
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
            </>
          ) : null}
        </div>
        {!recordOnly ? (
          <>
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

        <Field label="담당자 (선택)">
          <input
            className={inputClass}
            maxLength={100}
            value={worker}
            onChange={(e) => setWorker(e.target.value)}
          />
        </Field>
      </fieldset>
      {!recordOnly && (houses.isPending || varieties.isPending) ? (
        <p role="status">품종과 농장 배치를 불러오는 중입니다.</p>
      ) : null}
      {!recordOnly && (houses.error || varieties.error) ? (
        <p role="alert">
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
        <p role="alert" className="text-sm text-red-700">
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
          {saving ? "저장 중…" : "실제 도착 저장"}
        </button>
      </div>
    </form>
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
