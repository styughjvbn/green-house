"use client";

import type { FormEvent } from "react";
import type { BedZone, HouseStatusSummary, OrchidGroup, PhysicalBed } from "@/entities/farm/types";
import type { WorkRecordFormState } from "../../model/types";
import { SelectField, TextField } from "./FormFields";
import { TargetSelectorFields } from "./TargetSelectorFields";

type WorkRecordCreateFormProps = {
  bedZones: BedZone[];
  errorMessage: string | null;
  form: WorkRecordFormState;
  houses: HouseStatusSummary[];
  orchidGroups: OrchidGroup[];
  physicalBeds: PhysicalBed[];
  safeBedZoneId: string;
  safeOrchidGroupId: string;
  safePhysicalBedId: string;
  saving: boolean;
  workTypes: string[];
  onChange: <K extends keyof WorkRecordFormState>(field: K, value: WorkRecordFormState[K]) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function WorkRecordCreateForm({
  bedZones,
  errorMessage,
  form,
  houses,
  orchidGroups,
  physicalBeds,
  safeBedZoneId,
  safeOrchidGroupId,
  safePhysicalBedId,
  saving,
  workTypes,
  onChange,
  onSubmit,
}: WorkRecordCreateFormProps) {
  return (
    <section className="rounded-md border border-[#d7ddd4] bg-white p-4 shadow-sm">
      <div>
        <p className="text-sm font-semibold text-[#3d6f91]">작업 등록</p>
        <h2 className="mt-1 text-2xl font-semibold">새 작업 이력</h2>
      </div>

      <form className="mt-4 space-y-3" onSubmit={onSubmit}>
        <div className="grid grid-cols-2 gap-3">
          <SelectField label="작업 유형" value={form.workType} onChange={(value) => onChange("workType", value)}>
            {workTypes.map((workType) => (
              <option key={workType} value={workType}>
                {workType}
              </option>
            ))}
          </SelectField>
          <TextField label="작업일" required type="date" value={form.workDate} onChange={(value) => onChange("workDate", value)} />
        </div>

        <TargetSelectorFields
          bedZones={bedZones}
          form={form}
          houses={houses}
          orchidGroups={orchidGroups}
          physicalBeds={physicalBeds}
          safeBedZoneId={safeBedZoneId}
          safeOrchidGroupId={safeOrchidGroupId}
          safePhysicalBedId={safePhysicalBedId}
          onChange={onChange}
        />

        <div className="grid grid-cols-2 gap-3">
          <TextField label="자재명" value={form.materialName} onChange={(value) => onChange("materialName", value)} />
          <TextField label="희석 배수" value={form.dilutionRatio} onChange={(value) => onChange("dilutionRatio", value)} />
        </div>

        <div className="grid grid-cols-2 gap-3">
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

        {errorMessage ? <p className="rounded-md bg-[#fff1ec] p-3 text-sm text-[#9b341e]">{errorMessage}</p> : null}

        <button className="w-full rounded-md bg-[#159447] px-4 py-3 text-sm font-semibold text-white disabled:opacity-60" disabled={saving} type="submit">
          {saving ? "저장 중" : "작업 이력 저장"}
        </button>
      </form>
    </section>
  );
}

