"use client";

import { FormEvent, useMemo, useState } from "react";
import type {
  BedZone,
  OrchidGroup,
  VarietyOption,
} from "@/entities/farm/types";
import { nullableNumber, nullableText } from "../../lib/orchidManagementUtils";
import type { MutationPayload, OrchidFormState } from "../../model/types";
import TextField from "./TextField";
import VarietySearchSelect from "./VarietySearchSelect";

export default function OrchidGroupForm({
  initialValue,
  mode,
  saving,
  targetZone,
  onCancel,
  onSubmit,
}: {
  initialValue: OrchidGroup | null;
  mode: "CREATE" | "EDIT";
  saving: boolean;
  targetZone: BedZone | null;
  onCancel: () => void;
  onSubmit: (payload: MutationPayload) => Promise<void>;
}) {
  const initialVariety = useMemo<VarietyOption | null>(
    () =>
      initialValue?.varietyId != null
        ? {
            id: initialValue.varietyId,
            genus: initialValue.genus ?? "",
            name: initialValue.varietyName,
            defaultPotSize: initialValue.potSize,
            active: true,
          }
        : null,
    [initialValue],
  );

  const [form, setForm] = useState<OrchidFormState>(() => ({
    varietyId: initialValue?.varietyId ? String(initialValue.varietyId) : "",
    varietyQuery: "",
    genus: initialValue?.genus ?? "",
    varietyName: initialValue?.varietyName ?? "",
    quantity: initialValue ? String(initialValue.quantity) : "1",
    potSize: initialValue?.potSize ?? "",
    ageYear: initialValue?.ageYear ? String(initialValue.ageYear) : "",
    status: initialValue?.status ?? "정상",
    placementType: initialValue?.placementType ?? "",
    trayCount: initialValue?.trayCount ? String(initialValue.trayCount) : "",
    splitPlacementAllowed: initialValue?.splitPlacementAllowed ?? false,
    startPosition:
      initialValue?.startPosition != null
        ? String(initialValue.startPosition)
        : "",
    endPosition:
      initialValue?.endPosition != null ? String(initialValue.endPosition) : "",
    memo: initialValue?.memo ?? "",
  }));

  function updateField<K extends keyof OrchidFormState>(
    field: K,
    value: OrchidFormState[K],
  ) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function handleSelectVariety(option: VarietyOption) {
    setForm((current) => ({
      ...current,
      varietyId: String(option.id),
      varietyQuery: option.name,
      genus: option.genus,
      varietyName: option.name,
      potSize: current.potSize || option.defaultPotSize || "",
    }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.varietyId) {
      return;
    }

    void onSubmit({
      varietyId: Number(form.varietyId),
      quantity: Number(form.quantity),
      potSize: nullableText(form.potSize),
      ageYear: nullableNumber(form.ageYear),
      status: form.status.trim(),
      placementType: nullableText(form.placementType),
      trayCount: nullableNumber(form.trayCount),
      splitPlacementAllowed: form.splitPlacementAllowed,
      startPosition: nullableNumber(form.startPosition),
      endPosition: nullableNumber(form.endPosition),
      memo: nullableText(form.memo),
    });
  }

  return (
    <section className="rounded-md border border-[#b9d0ff] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#246df2]">
            {mode === "CREATE" ? "난 묶음 추가" : "난 묶음 수정"}
          </p>
          <h3 className="mt-1 text-base font-semibold">
            {targetZone?.name ?? "구역 선택 필요"}
          </h3>
          {targetZone ? (
            <p className="mt-1 text-xs font-semibold text-[#5c6a60]">
              {targetZone.houseNumber}동 {targetZone.physicalBedNumber}배드
            </p>
          ) : null}
        </div>
        <button
          className="rounded-md border border-[#d7ddd4] px-2 py-1.5 text-xs font-semibold"
          onClick={onCancel}
          type="button"
        >
          닫기
        </button>
      </div>
      <form className="mt-3 space-y-2" onSubmit={handleSubmit}>
        <VarietySearchSelect
          disabled={saving}
          selectedVariety={
            form.varietyId
              ? {
                  id: Number(form.varietyId),
                  genus: form.genus,
                  name: form.varietyName,
                  defaultPotSize: form.potSize || null,
                  active: true,
                }
              : initialVariety
          }
          onSelect={handleSelectVariety}
        />
        <div className="rounded-md border border-[#dbe1da] bg-[#f8faf7] px-3 py-2 text-xs text-[#435047]">
          <p className="font-semibold">선택 품종</p>
          <p className="mt-1">
            {form.varietyName || "선택 안 됨"}
            {form.genus ? ` / ${form.genus}` : ""}
          </p>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="수량"
            required
            type="number"
            value={form.quantity}
            onChange={(value) => updateField("quantity", value)}
          />
          <TextField
            label="초기 년생"
            type="number"
            value={form.ageYear}
            onChange={(value) => updateField("ageYear", value)}
          />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="화분 크기"
            value={form.potSize}
            onChange={(value) => updateField("potSize", value)}
          />
          <label className="block">
            <span className="text-sm font-semibold text-[#435047]">상태</span>
            <select
              className="mt-1 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
              value={form.status}
              onChange={(event) => updateField("status", event.target.value)}
            >
              <option value="정상">정상</option>
              <option value="주의">주의</option>
              <option value="이상">이상</option>
              <option value="판매 가능">판매 가능</option>
            </select>
          </label>
        </div>
        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="시작 위치"
            type="number"
            value={form.startPosition}
            onChange={(value) => updateField("startPosition", value)}
          />
          <TextField
            label="종료 위치"
            type="number"
            value={form.endPosition}
            onChange={(value) => updateField("endPosition", value)}
          />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <label className="block">
            <span className="text-sm font-semibold text-[#435047]">
              배치 규격
            </span>
            <select
              className="mt-1 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
              value={resolvePlacementSelectValue(form.placementType)}
              onChange={(event) =>
                updateField(
                  "placementType",
                  event.target.value === "CUSTOM"
                    ? "CUSTOM:"
                    : event.target.value,
                )
              }
            >
              <option value="">선택</option>
              <option value="TRAY_15">15구 트레이</option>
              <option value="TRAY_20">20구 트레이</option>
              <option value="TRAY_24">24구 트레이</option>
              <option value="SINGLE_POT">단독 화분</option>
              <option value="HANGING">행잉</option>
              <option value="CUSTOM">기타</option>
            </select>
          </label>
          <TextField
            label="판 수"
            required={usesTrayUnits(form.placementType)}
            type="number"
            value={form.trayCount}
            onChange={(value) => updateField("trayCount", value)}
          />
        </div>
        {form.placementType.startsWith("CUSTOM:") ? (
          <TextField
            label="기타 배치 규격명"
            required
            value={form.placementType.slice(7)}
            onChange={(value) =>
              updateField("placementType", `CUSTOM:${value}`)
            }
          />
        ) : null}
        <label className="flex items-center gap-2 rounded-md border border-[#dbe1da] bg-[#f8faf7] px-3 py-2 text-sm font-semibold text-[#435047]">
          <input
            checked={form.splitPlacementAllowed}
            className="accent-[#159447]"
            type="checkbox"
            onChange={(event) =>
              updateField("splitPlacementAllowed", event.target.checked)
            }
          />
          여러 구간으로 나눠 배치 가능
        </label>
        <label className="block">
          <span className="text-sm font-semibold text-[#435047]">메모</span>
          <textarea
            className="mt-1 min-h-16 w-full rounded-md border border-[#cfd8cc] px-2 py-1.5 text-sm"
            value={form.memo}
            onChange={(event) => updateField("memo", event.target.value)}
          />
        </label>
        <button
          className="w-full rounded-md bg-[#159447] px-3 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
          disabled={saving || !targetZone || !form.varietyId}
          type="submit"
        >
          {saving ? "저장 중..." : "저장"}
        </button>
      </form>
    </section>
  );
}

function resolvePlacementSelectValue(value: string) {
  return value.startsWith("CUSTOM:") ? "CUSTOM" : value;
}

function usesTrayUnits(value: string) {
  return value.startsWith("TRAY_") || value.startsWith("CUSTOM:");
}
