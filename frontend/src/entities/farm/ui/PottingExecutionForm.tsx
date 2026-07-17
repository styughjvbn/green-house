"use client";

import { useState } from "react";
import type { Dispatch, ReactNode, SetStateAction } from "react";
import { Plus, Trash2 } from "lucide-react";
import type { House } from "@/entities/farm/types";
import { isStandardPotSize, POT_SIZE_OPTIONS } from "@/entities/farm/potSizes";
import {
  FarmPlacementField,
  type FarmPlacementSelection,
} from "@/entities/farm/ui/FarmPlacementPicker";
import { createUuid } from "@/shared/lib/id";

type PottingResultRow = {
  key: string;
  actualQuantity: string;
  potSize: string;
  ageYear: string;
  placementType: string;
  placement: FarmPlacementSelection | null;
};

export type PottingExecutionValues = {
  pottingDate: string;
  results: Array<{
    bedZoneId: number;
    quantity: number;
    potSize?: string;
    ageYear?: number;
    placementType?: string;
    trayCount?: number;
    splitPlacementAllowed: boolean;
    startPosition: number;
    endPosition: number;
    memo?: string;
  }>;
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
  const [rows, setRows] = useState<PottingResultRow[]>(() => [
    newResultRow(initialActualQuantity, initialPotSize, initialAgeYear),
  ]);
  const [worker, setWorker] = useState(initialWorker ?? "");
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!pottingDate) {
      setError("포트 작업일을 입력해주세요.");
      return;
    }
    if (rows.some((row) => !isValidResultRow(row))) {
      setError("각 결과 난 묶음의 수량, 년생, 배치 위치를 확인해주세요.");
      return;
    }
    if (rows.some((row) => row.placementType === "CUSTOM:")) {
      setError("기타 배치 규격명을 입력해주세요.");
      return;
    }
    if (hasOverlappingPlacements(rows)) {
      setError("결과 난 묶음끼리 배치 칸이 겹칩니다.");
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await onSubmit({
        pottingDate,
        results: rows.map((row) => ({
          bedZoneId: row.placement!.bedZoneId,
          quantity: Number(row.actualQuantity),
          potSize: row.potSize.trim() || undefined,
          ageYear: row.ageYear ? Number(row.ageYear) : undefined,
          placementType: row.placementType.trim() || undefined,
          splitPlacementAllowed: false,
          startPosition: row.placement!.startPosition,
          endPosition: row.placement!.endPosition,
        })),
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
      <div className="space-y-3 md:col-span-2">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-bold text-[#27332b]">결과 난 묶음</p>
            <p className="text-xs font-normal text-[#6a766e]">
              생성 수량 합계 {resultQuantityTotal(rows).toLocaleString()}개
            </p>
          </div>
          <button
            className="inline-flex items-center gap-1 rounded-md border border-[#d4dbd5] px-3 py-1.5 text-xs font-semibold"
            type="button"
            onClick={() =>
              setRows((current) => [
                ...current,
                newResultRow(null, initialPotSize, initialAgeYear),
              ])
            }
          >
            <Plus className="h-3.5 w-3.5" aria-hidden="true" /> 난 묶음 추가
          </button>
        </div>
        {rows.map((row, index) => (
          <section key={row.key} className="rounded-md border bg-[#f8faf7] p-3">
            <div className="mb-3 flex items-center justify-between">
              <p className="text-xs font-bold">결과 {index + 1}</p>
              {rows.length > 1 ? (
                <button
                  type="button"
                  aria-label={`결과 ${index + 1} 삭제`}
                  onClick={() =>
                    setRows((current) =>
                      current.filter((candidate) => candidate.key !== row.key),
                    )
                  }
                >
                  <Trash2
                    className="h-4 w-4 text-[#a33a24]"
                    aria-hidden="true"
                  />
                </button>
              ) : null}
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <FarmPlacementField
                dialogDescription="구역을 고른 뒤 포트 작업 결과가 차지할 시작 칸과 끝 칸을 지정하세요."
                dialogTitle={`포트 작업 결과 ${index + 1} 배치 위치`}
                houses={houses}
                value={row.placement}
                onChange={(placement) =>
                  updateRow(setRows, row.key, { placement })
                }
              />
              <Field label="실제 생성 수량">
                <input
                  className={inputClass}
                  min={1}
                  required
                  type="number"
                  value={row.actualQuantity}
                  onChange={(event) =>
                    updateRow(setRows, row.key, {
                      actualQuantity: event.target.value,
                    })
                  }
                />
              </Field>
              <PotSizeField
                value={row.potSize}
                onChange={(potSize) => updateRow(setRows, row.key, { potSize })}
              />
              <Field label="초기 년생">
                <input
                  className={inputClass}
                  min={0}
                  type="number"
                  value={row.ageYear}
                  onChange={(event) =>
                    updateRow(setRows, row.key, { ageYear: event.target.value })
                  }
                />
              </Field>
              <PlacementTypeField
                value={row.placementType}
                onChange={(placementType) =>
                  updateRow(setRows, row.key, { placementType })
                }
              />
            </div>
          </section>
        ))}
      </div>
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

function newResultRow(
  quantity: number | null | undefined,
  potSize: string | null | undefined,
  ageYear: number | null | undefined,
): PottingResultRow {
  return {
    key: createUuid(),
    actualQuantity: quantity == null ? "" : String(quantity),
    potSize: potSize ?? "",
    ageYear: ageYear == null ? "" : String(ageYear),
    placementType: "",
    placement: null,
  };
}

function updateRow(
  setRows: Dispatch<SetStateAction<PottingResultRow[]>>,
  key: string,
  patch: Partial<PottingResultRow>,
) {
  setRows((current) =>
    current.map((row) => (row.key === key ? { ...row, ...patch } : row)),
  );
}

function isValidResultRow(row: PottingResultRow) {
  const quantity = Number(row.actualQuantity);
  const ageYear = row.ageYear ? Number(row.ageYear) : undefined;
  return (
    row.placement != null &&
    Number.isInteger(quantity) &&
    quantity >= 1 &&
    (ageYear == null || (Number.isInteger(ageYear) && ageYear >= 0))
  );
}

function resultQuantityTotal(rows: PottingResultRow[]) {
  return rows.reduce((total, row) => {
    const quantity = Number(row.actualQuantity);
    return total + (Number.isFinite(quantity) ? quantity : 0);
  }, 0);
}

function hasOverlappingPlacements(rows: PottingResultRow[]) {
  for (let leftIndex = 0; leftIndex < rows.length; leftIndex += 1) {
    const left = rows[leftIndex].placement;
    if (!left) continue;
    for (
      let rightIndex = leftIndex + 1;
      rightIndex < rows.length;
      rightIndex += 1
    ) {
      const right = rows[rightIndex].placement;
      if (
        right &&
        left.bedZoneId === right.bedZoneId &&
        left.startPosition < right.endPosition &&
        right.startPosition < left.endPosition
      ) {
        return true;
      }
    }
  }
  return false;
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
