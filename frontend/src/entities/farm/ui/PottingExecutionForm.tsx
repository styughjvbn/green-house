"use client";

import { useState } from "react";
import type { ReactNode } from "react";
import type { House } from "@/entities/farm/types";
import { isStandardPotSize, POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";
import {
  FarmPlacementField,
  type FarmPlacementSelection,
} from "@/entities/farm/ui/FarmPlacementPicker";

export type PottingExecutionValues = {
  pottingDate: string;
  actualQuantity: number;
  potSize?: string;
  ageYear?: number;
  placementType?: string;
  bedZoneId: number;
  startPosition: number;
  endPosition: number;
  worker?: string;
  memo?: string;
};

export function PottingExecutionForm({
  houses,
  initialActualQuantity,
  initialAgeYear,
  initialPotSize,
  initialWorker,
  subject,
  submitLabel = "포트 작업 완료",
  onCancel,
  onSubmit,
}: {
  houses: House[];
  initialActualQuantity?: number | null;
  initialAgeYear?: number | null;
  initialPotSize?: string | null;
  initialWorker?: string | null;
  subject: string;
  submitLabel?: string;
  onCancel: () => void;
  onSubmit: (values: PottingExecutionValues) => Promise<void>;
}) {
  const [pottingDate, setPottingDate] = useState(
    new Date().toISOString().slice(0, 10),
  );
  const [actualQuantity, setActualQuantity] = useState(
    initialActualQuantity == null ? "" : String(initialActualQuantity),
  );
  const [potSize, setPotSize] = useState(initialPotSize ?? "");
  const [ageYear, setAgeYear] = useState(
    initialAgeYear == null ? "" : String(initialAgeYear),
  );
  const [placementType, setPlacementType] = useState("");
  const [placement, setPlacement] = useState<FarmPlacementSelection | null>(
    null,
  );
  const [worker, setWorker] = useState(initialWorker ?? "");
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    const quantity = Number(actualQuantity);
    const age = ageYear ? Number(ageYear) : undefined;
    if (!pottingDate) {
      setError("포트 작업일을 입력해주세요.");
      return;
    }
    if (!Number.isInteger(quantity) || quantity < 1) {
      setError("실제 생성 수량을 1 이상의 정수로 입력해주세요.");
      return;
    }
    if (age != null && (!Number.isInteger(age) || age < 0)) {
      setError("초기 년생을 0 이상의 정수로 입력해주세요.");
      return;
    }
    if (!placement) {
      setError("배치 위치를 선택해주세요.");
      return;
    }
    if (placementType === "CUSTOM:") {
      setError("기타 배치 규격명을 입력해주세요.");
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await onSubmit({
        pottingDate,
        actualQuantity: quantity,
        potSize: potSize.trim() || undefined,
        ageYear: age,
        placementType: placementType.trim() || undefined,
        bedZoneId: placement.bedZoneId,
        startPosition: placement.startPosition,
        endPosition: placement.endPosition,
        worker: worker.trim() || undefined,
        memo: memo.trim() || undefined,
      });
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "포트 작업을 저장하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <form
      className="mt-4 grid gap-3 md:grid-cols-2"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <Field label="입고 품종">
        <input className={inputClass} disabled value={subject} />
      </Field>
      <Field label="포트 작업일">
        <input
          className={inputClass}
          required
          type="date"
          value={pottingDate}
          onChange={(event) => setPottingDate(event.target.value)}
        />
      </Field>
      <Field label="실제 생성 수량">
        <input
          className={inputClass}
          min={1}
          required
          type="number"
          value={actualQuantity}
          onChange={(event) => setActualQuantity(event.target.value)}
        />
      </Field>
      <PotSizeField value={potSize} onChange={setPotSize} />
      <Field label="초기 년생">
        <input
          className={inputClass}
          min={0}
          type="number"
          value={ageYear}
          onChange={(event) => setAgeYear(event.target.value)}
        />
      </Field>
      <FarmPlacementField
        dialogDescription="구역을 고른 뒤 포트 작업 결과가 차지할 시작 칸과 끝 칸을 지정하세요."
        dialogTitle="포트 작업 배치 위치 선택"
        houses={houses}
        value={placement}
        onChange={(nextPlacement) => {
          setPlacement(nextPlacement);
          setPlacementType("");
        }}
      />
      <PlacementTypeField value={placementType} onChange={setPlacementType} />
      <Field label="작업자">
        <input
          className={inputClass}
          value={worker}
          onChange={(event) => setWorker(event.target.value)}
        />
      </Field>
      <label className="space-y-1 text-xs font-semibold text-[#425047] md:col-span-2">
        <span>메모</span>
        <textarea
          className="min-h-24 w-full rounded-md border border-[#d7ddd8] bg-white px-3 py-2 text-sm outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]"
          value={memo}
          onChange={(event) => setMemo(event.target.value)}
        />
      </label>
      {error ? (
        <p className="rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e] md:col-span-2">
          {error}
        </p>
      ) : null}
      <div className="flex justify-end gap-2 md:col-span-2">
        <button
          className="rounded-md border border-[#d4dbd5] px-4 py-2 text-sm font-semibold"
          disabled={saving}
          type="button"
          onClick={onCancel}
        >
          취소
        </button>
        <button
          className="rounded-md bg-[#159447] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          disabled={saving}
          type="submit"
        >
          {saving ? "처리 중" : submitLabel}
        </button>
      </div>
    </form>
  );
}

function PotSizeField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <Field label="화분 크기">
      <select
        className={inputClass}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {!isStandardPotSize(value) ? (
          <option disabled value={value}>
            검수 필요: {value}
          </option>
        ) : null}
        {POT_SIZE_OPTIONS.map((option) => (
          <option key={option.value || "unspecified"} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </Field>
  );
}

const PLACEMENT_TYPE_OPTIONS = [
  { value: "TRAY_12", label: "12구 트레이" },
  { value: "TRAY_15", label: "15구 트레이" },
  { value: "TRAY_20", label: "20구 트레이" },
  { value: "TRAY_24", label: "24구 트레이" },
  { value: "SINGLE_POT", label: "단독 화분" },
  { value: "HANGING", label: "행잉" },
  { value: "CUSTOM", label: "기타" },
];

function PlacementTypeField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  const selectValue = value.startsWith("CUSTOM:") ? "CUSTOM" : value;
  return (
    <div className="space-y-2">
      <Field label="배치 규격">
        <select
          className={inputClass}
          value={selectValue}
          onChange={(event) =>
            onChange(
              event.target.value === "CUSTOM" ? "CUSTOM:" : event.target.value,
            )
          }
        >
          <option value="">선택</option>
          {PLACEMENT_TYPE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </Field>
      {selectValue === "CUSTOM" ? (
        <Field label="기타 배치 규격명">
          <input
            className={inputClass}
            required
            value={value.slice(7)}
            onChange={(event) => onChange(`CUSTOM:${event.target.value}`)}
          />
        </Field>
      ) : null}
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="min-w-0 space-y-1 text-xs font-semibold text-[#425047]">
      <span>{label}</span>
      {children}
    </label>
  );
}

const inputClass =
  "h-9 w-full rounded-md border border-[#d7ddd8] bg-white px-3 text-sm font-normal text-[#27332b] outline-none focus:border-[#159447] focus:ring-1 focus:ring-[#159447]";
