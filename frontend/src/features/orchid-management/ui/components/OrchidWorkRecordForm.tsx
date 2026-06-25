"use client";

import type { FormEvent } from "react";
import type { BedZone, OrchidGroup } from "@/entities/farm/types";
import type { WorkRecordQuickFormState } from "../../model/types";
import TextField, { SelectField } from "./TextField";

type OrchidWorkRecordFormProps = {
  form: WorkRecordQuickFormState;
  resolvedZone: BedZone | null;
  saving: boolean;
  selectedOrchidGroup: OrchidGroup | null;
  workTypes: string[];
  onCancel: () => void;
  onChange: <K extends keyof WorkRecordQuickFormState>(field: K, value: WorkRecordQuickFormState[K]) => void;
  onSubmit: () => Promise<void>;
};

export default function OrchidWorkRecordForm({
  form,
  resolvedZone,
  saving,
  selectedOrchidGroup,
  workTypes,
  onCancel,
  onChange,
  onSubmit,
}: OrchidWorkRecordFormProps) {
  const targetLabel = selectedOrchidGroup
    ? `${selectedOrchidGroup.varietyName} / ${selectedOrchidGroup.quantity}분`
    : resolvedZone?.name ?? "선택 대상";

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit();
  }

  return (
    <section className="rounded-md border border-[#b9d0ff] bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-[#246df2]">작업 기록 추가</p>
          <h3 className="mt-1 text-lg font-semibold">{targetLabel}</h3>
        </div>
        <button className="rounded-md border border-[#d7ddd4] px-2 py-1 text-xs font-semibold" type="button" onClick={onCancel}>
          닫기
        </button>
      </div>

      <form className="mt-3 space-y-3" onSubmit={handleSubmit}>
        <div className="grid grid-cols-2 gap-2">
          <SelectField label="작업 유형" value={form.workType} onChange={(value) => onChange("workType", value)}>
            {workTypes.map((workType) => (
              <option key={workType} value={workType}>
                {workType}
              </option>
            ))}
          </SelectField>
          <TextField label="작업일" required type="date" value={form.workDate} onChange={(value) => onChange("workDate", value)} />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <TextField label="자재명" value={form.materialName} onChange={(value) => onChange("materialName", value)} />
          <TextField label="희석 배수" value={form.dilutionRatio} onChange={(value) => onChange("dilutionRatio", value)} />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <TextField label="수량" value={form.quantity} onChange={(value) => onChange("quantity", value)} />
          <TextField label="작업자" value={form.worker} onChange={(value) => onChange("worker", value)} />
        </div>
        <label className="block">
          <span className="text-sm font-semibold text-[#435047]">메모</span>
          <textarea
            className="mt-1 min-h-20 w-full rounded-md border border-[#cfd8cc] px-3 py-2 text-sm"
            value={form.memo}
            onChange={(event) => onChange("memo", event.target.value)}
          />
        </label>
        <button className="w-full rounded-md bg-[#246df2] px-3 py-2 text-sm font-semibold text-white disabled:opacity-60" disabled={saving} type="submit">
          {saving ? "저장 중" : "작업 기록 저장"}
        </button>
      </form>
    </section>
  );
}
